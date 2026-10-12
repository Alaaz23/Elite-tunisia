package tn.elitetunisia.quote;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.elitetunisia.booking.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.pricing.RentalPricingService;
@Service
public class QuoteService {
    private final QuoteRepository quotes;
    private final QuoteEventRepository events;
    private final ConfiguredServiceRepository services;
    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final RentalPricingService pricing;
    private final QuoteTaxPolicy taxes;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    public QuoteService(QuoteRepository quotes, QuoteEventRepository events, ConfiguredServiceRepository services, BookingService bookingService,
            BookingRepository bookings, RentalPricingService pricing, QuoteTaxPolicy taxes, AuditService audit, JdbcTemplate jdbc) {
        this.quotes = quotes; this.events = events; this.services = services; this.bookingService = bookingService;
        this.bookings = bookings; this.pricing = pricing; this.taxes = taxes; this.audit = audit; this.jdbc = jdbc;
    }
    @Transactional
    public QuoteSummary create(UUID bookingId, QuoteRequest request, UUID actor, String ip) {
        BookingEntity booking = bookingService.lockForCommercialOperation(bookingId);
        requirePending(booking);
        var previous = quotes.findFirstByBookingIdOrderByVersionDesc(bookingId);
        if (previous.isPresent() && previous.get().getStatus() == QuoteStatus.DRAFT) throw new QuoteException("QUOTE_DRAFT_EXISTS", "Modifiez le brouillon existant avant de créer une autre version.");
        QuoteEntity quote = new QuoteEntity(booking, "DEV-" + Year.now(ZoneId.of(bookingService.timeZone())) + "-" + String.format(Locale.ROOT, "%06d", jdbc.queryForObject("SELECT nextval('quote_reference_seq')", Long.class)), previous.map(value -> value.getVersion() + 1).orElse(1));
        calculate(quote, request, List.of());
        if (previous.isPresent() && previous.get().getStatus() == QuoteStatus.SENT) {
            previous.get().transition(QuoteStatus.CANCELLED); recordEvent(previous.get(), actor, "QUOTE_CANCELLED", "Remplacé par une nouvelle version", ip);
        }
        quotes.saveAndFlush(quote); recordEvent(quote, actor, "QUOTE_CREATED", null, ip);
        return summary(quote, true, true);
    }
    @Transactional
    public QuoteSummary update(UUID id, QuoteRequest request, UUID actor, String ip) {
        QuoteEntity quote = lock(id, null); requirePending(quote.getBooking()); requireLatest(quote);
        if (quote.getStatus() != QuoteStatus.DRAFT) throw new QuoteException("QUOTE_IMMUTABLE", "Créez une nouvelle version pour modifier ce devis.");
        var snapshots = quote.getLines().stream().map(QuoteLineEntity::summary).toList();
        quote.getLines().clear(); quotes.flush(); calculate(quote, request, snapshots); quotes.flush(); recordEvent(quote, actor, "QUOTE_UPDATED", null, ip);
        return summary(quote, true, true);
    }
    private void calculate(QuoteEntity quote, QuoteRequest request, List<QuoteLineSummary> snapshots) {
        BookingEntity booking = quote.getBooking(); bookingService.validateDates(booking.getStartDateTime(), booking.getEndDateTime());
        if (request.validUntil() == null || !request.validUntil().isAfter(Instant.now()) || request.validUntil().getNano() % 1000 != 0) throw new QuoteException("QUOTE_VALIDITY_INVALID", "Définissez une validité future à précision maximale de six décimales.");
        var vehicle = booking.getVehicle();
        if (!vehicle.getCurrency().equals(quote.getCurrency())) throw new QuoteException("QUOTE_CURRENCY_MISMATCH", "La devise du tarif véhicule a changé; aucune conversion automatique n'est autorisée.");
        RentalPricingService.RentalPrice rental;
        BigDecimal rentalRate = snapshots.stream().filter(line -> line.type().equals("VEHICLE_RENTAL")).map(QuoteLineSummary::unitPrice).findFirst().orElse(vehicle.getPricePerDay());
        try { rental = pricing.calculate(rentalRate, booking.getStartDateTime(), booking.getEndDateTime()); }
        catch (IllegalArgumentException exception) { throw new QuoteException("QUOTE_PRICE_INVALID", "La période ou le tarif dépasse les limites autorisées."); }
        add(quote, "VEHICLE_RENTAL", "Location véhicule", rental.days(), rental.dailyRate(), Map.of("vehicleId", vehicle.getId().toString()));
        if (request.withDriver()) {
            BigDecimal globalRate = snapshots.stream().filter(line -> line.type().equals("DRIVER")).map(line -> new BigDecimal(line.metadata().get("globalDailyRate"))).findFirst().orElse(vehicle.getPriceWithDriver());
            if (!vehicle.isWithDriverAvailable() || globalRate == null || globalRate.compareTo(rental.dailyRate()) < 0) throw new QuoteException("QUOTE_DRIVER_UNAVAILABLE", "Le tarif global avec chauffeur n'est pas configuré ou est incohérent.");
            add(quote, "DRIVER", "Supplément chauffeur", rental.days(), globalRate.subtract(rental.dailyRate()), Map.of("globalDailyRate", globalRate.toPlainString()));
        }
        Set<UUID> selected = new HashSet<>();
        for (var selection : request.services() == null ? List.<QuoteRequest.ServiceSelection>of() : request.services()) {
            if (!selected.add(selection.serviceId()) || selection.quantity() < 1 || selection.quantity() > 1000) throw new QuoteException("QUOTE_SERVICE_INVALID", "La sélection de services est invalide.");
            var service = services.findById(selection.serviceId()).filter(ConfiguredServiceEntity::isActive).orElseThrow(() -> new QuoteException("QUOTE_SERVICE_INVALID", "Le service n'est pas configuré ou actif."));
            if (!service.getCurrency().equals(quote.getCurrency())) throw new QuoteException("QUOTE_CURRENCY_MISMATCH", "Le service utilise une autre devise.");
            BigDecimal serviceRate = snapshots.stream().filter(line -> line.type().equals("EXTRA_SERVICE") && service.getId().toString().equals(line.metadata().get("serviceId"))).map(QuoteLineSummary::unitPrice).findFirst().orElse(service.getPrice());
            add(quote, "EXTRA_SERVICE", service.getName(), selection.quantity(), serviceRate, Map.of("serviceId", service.getId().toString()));
        }
        BigDecimal subtotal = quote.getLines().stream().map(QuoteLineEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3);
        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO.setScale(3) : request.discountAmount().setScale(3, RoundingMode.UNNECESSARY);
        BigDecimal tax = snapshots.isEmpty() ? taxes.amountFor(quote.getCurrency()) : quote.getTaxAmount();
        if (discount.signum() < 0 || discount.compareTo(subtotal) > 0 || subtotal.compareTo(RentalPricingService.MAX_AMOUNT) > 0 || subtotal.subtract(discount).add(tax).compareTo(RentalPricingService.MAX_AMOUNT) > 0) throw new QuoteException("QUOTE_PRICE_INVALID", "La remise ou le total est hors des limites autorisées.");
        if (discount.signum() > 0) add(quote, "DISCOUNT", "Remise agence", 1, discount, Map.of());
        if (tax.signum() > 0) add(quote, "TAX", "Taxe configurée", 1, tax, Map.of());
        quote.setDraft(request, subtotal, discount, tax);
    }
    private void add(QuoteEntity quote, String type, String description, int quantity, BigDecimal unitPrice, Map<String,String> metadata) {
        if (unitPrice.signum() < 0 || unitPrice.multiply(BigDecimal.valueOf(quantity)).compareTo(RentalPricingService.MAX_AMOUNT) > 0) throw new QuoteException("QUOTE_PRICE_INVALID", "Montant de ligne invalide.");
        quote.getLines().add(new QuoteLineEntity(quote, type, description, quantity, unitPrice, metadata, quote.getLines().size()));
    }
    @Transactional(noRollbackFor = QuoteException.class)
    public QuoteSummary send(UUID id, UUID actor, String ip) {
        QuoteEntity quote = lock(id, null); requirePending(quote.getBooking()); requireLatest(quote);
        if (!quote.coherentWith(quote.getBooking())) throw new QuoteException("QUOTE_BOOKING_CHANGED", "La demande a changé; créez une nouvelle version.");
        if (!quote.getValidUntil().isAfter(Instant.now())) throw new QuoteException("QUOTE_EXPIRED", "La validité du devis est dépassée.");
        quote.transition(QuoteStatus.SENT); quotes.flush(); recordEvent(quote, actor, "QUOTE_SENT", null, ip); return summary(quote, true, true);
    }
    @Transactional(noRollbackFor = QuoteException.class)
    public QuoteSummary customerAction(UUID id, UUID owner, String action, String message, String ip) {
        QuoteEntity quote = lock(id, owner); expireIfDue(quote);
        if (quote.getStatus() == QuoteStatus.EXPIRED) throw new QuoteException("QUOTE_EXPIRED", "Ce devis a expiré.");
        requirePending(quote.getBooking()); requireLatest(quote);
        if (quote.getStatus() != QuoteStatus.SENT) throw new QuoteException("INVALID_QUOTE_TRANSITION", "Seul un devis envoyé peut recevoir une réponse.");
        if (action.equals("accept")) {
            if (!quote.coherentWith(quote.getBooking())) throw new QuoteException("QUOTE_BOOKING_CHANGED", "La demande a changé depuis l'envoi du devis.");
            var availability = bookingService.availability(quote.getVehicleId(), quote.getStartDateTime(), quote.getEndDateTime());
            if (!availability.available()) throw new QuoteException("QUOTE_VEHICLE_UNAVAILABLE", "Le véhicule n'est plus disponible. Contactez l'agence.");
            quote.transition(QuoteStatus.ACCEPTED); recordEvent(quote, owner, "QUOTE_ACCEPTED", null, ip);
        } else if (action.equals("reject")) { quote.transition(QuoteStatus.REJECTED); recordEvent(quote, owner, "QUOTE_REJECTED", message, ip); }
        else if (action.equals("request-change")) { recordEvent(quote, owner, "QUOTE_CHANGE_REQUESTED", message, ip); }
        else throw new QuoteException("INVALID_QUOTE_TRANSITION", "Action inconnue.");
        quotes.flush(); return summary(quote, false, true);
    }
    @Transactional
    public QuoteSummary cancel(UUID id, UUID actor, String ip) {
        QuoteEntity quote = lock(id, null); quote.transition(QuoteStatus.CANCELLED); quotes.flush(); recordEvent(quote, actor, "QUOTE_CANCELLED", null, ip); return summary(quote, true, true);
    }
    @Transactional
    public QuoteSummary get(UUID id, UUID owner) {
        QuoteEntity quote = lock(id, owner);
        if (owner != null && quote.getStatus() == QuoteStatus.DRAFT) throw notFound();
        expireIfDue(quote); return summary(quote, owner == null, true);
    }
    @Transactional(readOnly = true)
    public PagedResponse<QuoteSummary> list(QuoteSearch filter, UUID owner) {
        Specification<QuoteEntity> spec = (root, query, builder) -> builder.conjunction();
        UUID customer = owner == null ? filter.customer() : owner;
        if (customer != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("customerId"), customer));
        if (owner != null) spec = spec.and((root, query, builder) -> builder.notEqual(root.get("status"), QuoteStatus.DRAFT));
        if (filter.booking() != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("booking").get("id"), filter.booking()));
        if (filter.vehicle() != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("vehicleId"), filter.vehicle()));
        if (filter.reference() != null && !filter.reference().isBlank()) spec = spec.and((root, query, builder) -> builder.like(builder.lower(root.get("reference")), "%" + filter.reference().trim().toLowerCase(Locale.ROOT) + "%"));
        if (filter.status() != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), filter.status()));
        if (filter.date() != null) spec = spec.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), filter.date()));
        if (filter.validUntil() != null) spec = spec.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("validUntil"), filter.validUntil()));
        var page = quotes.findAll(spec, PageRequest.of(filter.page() == null ? 0 : Math.max(0, filter.page()), filter.size() == null ? 25 : Math.min(100, Math.max(1, filter.size())), Sort.by("createdAt").descending().and(Sort.by("id"))));
        return new PagedResponse<>(page.map(quote -> summary(quote, owner == null, false)).getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
    @Transactional(readOnly = true)
    public Map<String,Object> options(UUID bookingId) {
        var booking = bookings.findById(bookingId).orElseThrow(() -> new BookingException("BOOKING_NOT_FOUND", "Demande introuvable."));
        var vehicle = booking.getVehicle();
        return Map.of("withDriverAvailable", vehicle.isWithDriverAvailable() && vehicle.getPriceWithDriver() != null && vehicle.getPriceWithDriver().compareTo(vehicle.getPricePerDay()) >= 0,
                "services", services.findByActiveTrueAndCurrencyOrderByNameAsc(booking.getCurrency()).stream().map(service -> Map.of("id", service.getId(), "name", service.getName(), "price", service.getPrice(), "currency", service.getCurrency())).toList());
    }
    @Transactional public void expire(UUID id) { expireIfDue(lock(id, null)); }
    private void expireIfDue(QuoteEntity quote) {
        if (quote.getStatus() == QuoteStatus.SENT && !quote.getValidUntil().isAfter(Instant.now())) {
            quote.transition(QuoteStatus.EXPIRED); quotes.flush(); recordEvent(quote, null, "QUOTE_EXPIRED", null, null);
        }
    }
    private QuoteEntity lock(UUID id, UUID owner) {
        QuoteEntity observed = (owner == null ? quotes.findById(id) : quotes.findByIdAndCustomerId(id, owner)).orElseThrow(this::notFound);
        bookingService.lockForCommercialOperation(observed.getBooking().getId());
        quotes.flush();
        entityManager.refresh(observed);
        return observed;
    }
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private void requirePending(BookingEntity booking) { if (booking.getStatus() != BookingStatus.PENDING) throw new QuoteException("QUOTE_BOOKING_INVALID", "La demande doit être en attente."); }
    private void requireLatest(QuoteEntity quote) {
        if (!quotes.findFirstByBookingIdOrderByVersionDesc(quote.getBooking().getId()).orElseThrow(this::notFound).getId().equals(quote.getId())) throw new QuoteException("QUOTE_SUPERSEDED", "Une version plus récente existe.");
    }
    private QuoteException notFound() { return new QuoteException("QUOTE_NOT_FOUND", "Le devis est introuvable."); }
    private void recordEvent(QuoteEntity quote, UUID actor, String action, String message, String ip) {
        events.saveAndFlush(new QuoteEventEntity(quote.getId(), actor, action, message)); audit.recordAudit(actor, action, "QUOTE", quote.getId(), ip, "SUCCESS");
    }
    private QuoteSummary summary(QuoteEntity quote, boolean admin, boolean detail) {
        var booking = quote.getBooking(); var customer = booking.getCustomer();
        return new QuoteSummary(quote.getId(), quote.getReference(), booking.getId(), booking.getReference(), booking.getStatus(), quote.getCustomerId(), customer.getFirstName() + " " + customer.getLastName(), quote.getVehicleId(), quote.getVehicleName(), quote.getStartDateTime(), quote.getEndDateTime(), quote.getPickupLocation(), quote.getDropoffLocation(), quote.getVersion(), quote.getStatus(), quote.getCurrency(), quote.getSubtotal(), quote.getDiscountAmount(), quote.getTaxAmount(), quote.getTotalAmount(), quote.getValidUntil(), quote.getCustomerNote(), admin ? quote.getInternalNote() : null, quote.getCreatedAt(), quote.getUpdatedAt(), quote.getSentAt(), quote.getAcceptedAt(), quote.getRejectedAt(), bookingService.timeZone(), detail ? quote.getLines().stream().map(QuoteLineEntity::summary).toList() : List.of(), detail ? events.findByQuoteIdOrderByCreatedAtAsc(quote.getId()).stream().map(QuoteEventEntity::summary).toList() : List.of());
    }
}