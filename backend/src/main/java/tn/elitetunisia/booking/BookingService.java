package tn.elitetunisia.booking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.vehicle.*;

@Service
public class BookingService {
    public static final List<BookingStatus> BLOCKING = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final CustomerRepository customers;
    private final BookingEventRepository events;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ZoneId businessZone;
    private final tn.elitetunisia.common.pricing.RentalPricingService pricing;
    private final tn.elitetunisia.quote.QuoteRepository quotes;
    private final BookingOperationRepository operations;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public BookingService(BookingRepository bookings, VehicleRepository vehicles, CustomerRepository customers,
            BookingEventRepository events, AuditService audit, JdbcTemplate jdbc,
            @Value("${app.booking.time-zone:Africa/Tunis}") String zone,
            tn.elitetunisia.common.pricing.RentalPricingService pricing, tn.elitetunisia.quote.QuoteRepository quotes,
            BookingOperationRepository operations) {
        this.bookings = bookings; this.vehicles = vehicles; this.customers = customers; this.events = events;
        this.audit = audit; this.jdbc = jdbc; this.businessZone = ZoneId.of(zone);
        this.pricing = pricing;
        this.quotes = quotes;
        this.operations = operations;
    }
    public String timeZone() { return businessZone.getId(); }
    public void validateDates(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start) || !start.isAfter(Instant.now())
            || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) {
            throw new BookingException("INVALID_DATE_RANGE", "La période doit être future, avec une fin postérieure et une précision maximale de six décimales de seconde.");
        }
    }
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID vehicleId, Instant start, Instant end) {
        validateDates(start, end);
        VehicleEntity vehicle = vehicle(vehicleId, false);
        if (!rentable(vehicle)) return new AvailabilityResponse(false, "VEHICLE_NOT_AVAILABLE");
        return bookings.overlaps(vehicleId, start, end, BLOCKING, null) == 0
                ? new AvailabilityResponse(true, null) : new AvailabilityResponse(false, "VEHICLE_ALREADY_BOOKED");
    }
    @Transactional(readOnly = true)
    public BookingPreview preview(BookingRequest request) {
        validateDates(request.startDateTime(), request.endDateTime());
        VehicleEntity vehicle = vehicle(request.vehicleId(), false);
        requireAvailable(vehicle, request.startDateTime(), request.endDateTime(), null);
        return price(vehicle, request.startDateTime(), request.endDateTime());
    }
    private BookingPreview price(VehicleEntity vehicle, Instant start, Instant end) {
        tn.elitetunisia.common.pricing.RentalPricingService.RentalPrice calculation;
        try { calculation = pricing.calculate(vehicle.getPricePerDay(), start, end); }
        catch (IllegalArgumentException exception) {
            throw new BookingException("INVALID_DATE_RANGE", "La période dépasse les limites de calcul autorisées.");
        }
        return new BookingPreview(vehicle.getId(), name(vehicle), start, end, calculation.days(), calculation.amount(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, calculation.amount(), vehicle.getCurrency(), timeZone());
    }
    @Transactional
    public BookingSummary create(BookingRequest request, UUID customerId, String ip) {
        validateDates(request.startDateTime(), request.endDateTime());
        var customer = customers.findById(customerId).orElseThrow(() -> new BookingException("BOOKING_ACCESS_DENIED", "Un compte client est requis."));
        VehicleEntity vehicle = vehicle(request.vehicleId(), true);
        requireAvailable(vehicle, request.startDateTime(), request.endDateTime(), null);
        Long sequence = jdbc.queryForObject("SELECT nextval('booking_reference_seq')", Long.class);
        String reference = "ET-" + Year.now(businessZone) + "-" + String.format(Locale.ROOT, "%06d", sequence);
        BookingEntity booking = bookings.saveAndFlush(new BookingEntity(reference, customer, vehicle, request,
            price(vehicle, request.startDateTime(), request.endDateTime())));
        recordEvent(booking, customerId, "BOOKING_CREATED", null, ip);
        return summary(booking, false, true);
    }
    @Transactional(readOnly = true)
    public BookingSummary customerGet(UUID id, UUID customerId) {
        return summary(own(id, customerId), false, true);
    }
    @Transactional(readOnly = true)
    public BookingSummary adminGet(UUID id) { return summary(find(id), true, true); }
    @Transactional(readOnly = true)
    public PagedResponse<BookingSummary> list(BookingSearch filter, UUID owner) {
        if (filter.startDate() != null && filter.endDate() != null && !filter.endDate().isAfter(filter.startDate())) {
            throw new BookingException("INVALID_DATE_RANGE", "Les bornes du filtre sont invalides.");
        }
        Specification<BookingEntity> spec = (root, query, builder) -> builder.conjunction();
        UUID customer = owner == null ? filter.customer() : owner;
        if (customer != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("customer").get("id"), customer));
        if (filter.vehicle() != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("vehicle").get("id"), filter.vehicle()));
        if (filter.status() != null) spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), filter.status()));
        if (filter.reference() != null && !filter.reference().isBlank()) spec = spec.and((root, query, builder) ->
                builder.like(builder.lower(root.get("reference")), "%" + filter.reference().trim().toLowerCase(Locale.ROOT) + "%"));
        if (filter.startDate() != null) spec = spec.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("startDateTime"), filter.startDate()));
        if (filter.endDate() != null) spec = spec.and((root, query, builder) -> builder.lessThan(root.get("startDateTime"), filter.endDate()));
        Page<BookingEntity> result = bookings.findAll(spec, PageRequest.of(filter.page() == null ? 0 : Math.max(0, filter.page()),
            filter.size() == null || filter.size() <= 0 ? 25 : Math.min(100, filter.size()), Sort.by("createdAt").descending().and(Sort.by("id"))));
        return new PagedResponse<>(result.map(booking -> summary(booking, owner == null, false)).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
    @Transactional
    public BookingSummary customerCancel(UUID id, UUID owner, String ip) {
        own(id, owner);
        BookingEntity booking = locked(id);
        if (!booking.getCustomer().getId().equals(owner)) throw notFound();
        change(booking, BookingStatus.CANCELLED, owner, ip);
        return summary(booking, false, true);
    }
    @Transactional
    public BookingSummary transition(UUID id, BookingStatus target, UUID actor, String ip) {
        BookingEntity booking = locked(id);
        // Idempotence: if already in target state, no second effect should occur
        if (booking.getStatus() == target) {
            return summary(booking, true, true);
        }
        if (!booking.getStatus().canTransitionTo(target)) {
            throw new BookingException("INVALID_STATUS_TRANSITION", "Cette réservation ne peut pas changer vers cet état.");
        }
        if (target == BookingStatus.IN_PROGRESS && !operations.existsByBookingIdAndAction(id, "PICKUP")) {
            throw new BookingException("INVALID_OPERATION", "Record the vehicle pickup before starting the booking.");
        }
        if (target == BookingStatus.COMPLETED && !operations.existsByBookingIdAndAction(id, "RETURN")) {
            throw new BookingException("INVALID_OPERATION", "Record the vehicle return before completing the booking.");
        }
        if (target == BookingStatus.CONFIRMED) {
            if (!booking.getStatus().canTransitionTo(target)) throw new BookingException("INVALID_STATUS_TRANSITION", "Cette réservation ne peut pas être confirmée.");
            validateDates(booking.getStartDateTime(), booking.getEndDateTime());
            requireAvailable(booking.getVehicle(), booking.getStartDateTime(), booking.getEndDateTime(), id);
            var proposal = quotes.findFirstByBookingIdOrderByVersionDesc(id);
            if (proposal.isPresent()) {
                var quote = proposal.get();
                if (quote.getStatus() != tn.elitetunisia.quote.QuoteStatus.ACCEPTED || !quote.coherentWith(booking)) throw new BookingException("QUOTE_NOT_ACCEPTED", "La version actuelle du devis doit être acceptée et cohérente avec la demande.");
                booking.applyCommercialPrice(quote);
            }
        }
        change(booking, target, actor, ip);
        return summary(booking, true, true);
    }
    @Transactional
    public BookingSummary update(UUID id, BookingUpdateRequest request, UUID actor, String ip) {
        BookingEntity booking = locked(id);
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingException("INVALID_STATUS_TRANSITION", "Seules les demandes et réservations confirmées peuvent être modifiées.");
        }
        booking.update(request);
        bookings.flush();
        recordEvent(booking, actor, "BOOKING_UPDATED", booking.getStatus(), ip);
        return summary(booking, true, true);
    }
    private void change(BookingEntity booking, BookingStatus target, UUID actor, String ip) {
        BookingStatus previous = booking.getStatus();
        booking.transition(target, Instant.now());
        bookings.flush();
        String action = switch (target) {
            case CONFIRMED -> "BOOKING_CONFIRMED"; case REJECTED -> "BOOKING_REJECTED";
            case CANCELLED -> "BOOKING_CANCELLED"; default -> "BOOKING_UPDATED";
        };
        recordEvent(booking, actor, action, previous, ip);
    }
    private void recordEvent(BookingEntity booking, UUID actor, String action, BookingStatus previous, String ip) {
        events.saveAndFlush(new BookingEventEntity(booking.getId(), actor, action, previous, booking.getStatus(), Instant.now()));
        audit.recordAudit(actor, action, "BOOKING", booking.getId(), ip, "SUCCESS");
    }
    private BookingEntity locked(UUID id) {
        BookingEntity observed = find(id);
        vehicle(observed.getVehicle().getId(), true);
        BookingEntity locked = bookings.findLocked(id).orElseThrow(this::notFound);
        entityManager.refresh(locked);
        return locked;
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public BookingEntity lockForCommercialOperation(UUID id) { return locked(id); }
    private BookingEntity own(UUID id, UUID owner) { return bookings.findByIdAndCustomerId(id, owner).orElseThrow(this::notFound); }
    private BookingEntity find(UUID id) { return bookings.findById(id).orElseThrow(this::notFound); }
    private BookingException notFound() { return new BookingException("BOOKING_NOT_FOUND", "La réservation demandée est introuvable."); }
    private VehicleEntity vehicle(UUID id, boolean lock) {
        return (lock ? vehicles.findLocked(id) : vehicles.findById(id)).orElseThrow(() ->
                new BookingException("VEHICLE_NOT_FOUND", "Le véhicule demandé est introuvable."));
    }
    private boolean rentable(VehicleEntity vehicle) {
        return vehicle.getStatus() == VehicleStatus.AVAILABLE && vehicle.getBrand().isActive() && vehicle.getCategory().isActive();
    }
    private void requireAvailable(VehicleEntity vehicle, Instant start, Instant end, UUID excluded) {
        if (!rentable(vehicle) || bookings.overlaps(vehicle.getId(), start, end, BLOCKING, excluded) > 0) {
            throw new BookingException("VEHICLE_NOT_AVAILABLE", "Ce véhicule n'est pas disponible pour cette période.");
        }
    }
    private String name(VehicleEntity vehicle) { return vehicle.getBrand().getName() + " " + vehicle.getModel(); }
    private BookingSummary summary(BookingEntity booking, boolean admin, boolean history) {
        var customer = booking.getCustomer();
        return new BookingSummary(booking.getId(), booking.getReference(), customer.getId(), customer.getFirstName() + " " + customer.getLastName(),
                booking.getVehicle().getId(), name(booking.getVehicle()), booking.getStartDateTime(), booking.getEndDateTime(),
                booking.getPickupLocation(), booking.getDropoffLocation(), booking.getStatus(), booking.getNumberOfDays(),
                booking.getBasePrice(), booking.getAdditionalCharges(), booking.getDiscount(), booking.getTax(), booking.getTotalAmount(),
                booking.getCurrency(), booking.getCustomerNote(), admin ? booking.getInternalNote() : null, booking.getCreatedAt(),
                booking.getUpdatedAt(), booking.getConfirmedAt(), booking.getCancelledAt(), timeZone(), history
                        ? events.findByBookingIdOrderByCreatedAtAsc(booking.getId()).stream().map(BookingEventEntity::summary).toList() : List.of());
    }
}