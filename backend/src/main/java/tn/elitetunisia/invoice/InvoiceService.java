package tn.elitetunisia.invoice;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.payment.PaymentEntity;
import tn.elitetunisia.payment.PaymentRepository;
import tn.elitetunisia.quote.QuoteEntity;
import tn.elitetunisia.quote.QuoteRepository;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final InvoiceEventRepository events;
    private final QuoteRepository quotes;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final JdbcTemplate jdbc;

    public InvoiceService(InvoiceRepository invoices, InvoiceEventRepository events, QuoteRepository quotes, BookingRepository bookings, PaymentRepository payments, JdbcTemplate jdbc) {
        this.invoices = invoices; this.events = events; this.quotes = quotes; this.bookings = bookings; this.payments = payments; this.jdbc = jdbc;
    }

    @Transactional
    public InvoiceEntity createFromQuote(UUID quoteId, UUID actorUserId, Integer dueDays) {
        QuoteEntity quote = quotes.findById(quoteId).orElseThrow(() -> tn.elitetunisia.common.exception.DomainException.notFound("QUOTE_NOT_FOUND"));
        if (quote.getStatus() != tn.elitetunisia.quote.QuoteStatus.ACCEPTED) throw tn.elitetunisia.common.exception.DomainException.conflict("QUOTE_NOT_ACCEPTED");

        BookingEntity booking = quote.getBooking();

        // generate robust reference using DB sequence
        Long seq = null;
        try {
            seq = jdbc.queryForObject("select nextval('invoice_seq')", Long.class);
        } catch (Exception ex) {
            // sequence not available (H2 test profile without flyway); fallback to time-based surrogate
            seq = System.currentTimeMillis() % 1000000L;
        }
        String ref = String.format("INV-%s-%06d", Instant.now().toString().substring(0,10).replaceAll("-",""), seq);

        Instant issued = Instant.now();
        Instant due = null; if (dueDays != null) due = issued.plusSeconds(dueDays.longValue()*86400L);

        InvoiceEntity invoice = new InvoiceEntity(ref, quote, booking, quote.getCustomerId(), quote.getCurrency(), quote.getSubtotal(), quote.getDiscountAmount(), quote.getTaxAmount(), quote.getTotalAmount(), issued, due);
        // copy lines
        IntStream.range(0, quote.getLines().size()).forEach(i -> {
            var qline = quote.getLines().get(i);
            var qsum = qline.summary();
            var line = new InvoiceLineEntity(qsum.type(), qsum.description(), qsum.quantity(), qsum.unitPrice(), java.util.Map.of(), qsum.position());
            invoice.addLine(line);
        });

        invoices.saveAndFlush(invoice);
        events.saveAndFlush(new InvoiceEventEntity(invoice, "CREATED", "Invoice created from quote " + quote.getReference()));
        return invoice;
    }

    @Transactional
    public InvoiceEntity createReturnChargeInvoice(BookingEntity booking, java.math.BigDecimal charge) {
        if (charge == null || charge.signum() <= 0) throw new IllegalArgumentException("A positive return charge is required.");
        Long sequence = jdbc.queryForObject("select nextval('invoice_seq')", Long.class);
        String reference = String.format("INV-%s-%06d",
                Instant.now().toString().substring(0, 10).replace("-", ""), sequence);
        Instant issued = Instant.now();
        InvoiceEntity invoice = new InvoiceEntity(reference, null, booking, booking.getCustomer().getId(),
                booking.getCurrency(), charge, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, charge, issued, null);
        invoice.addLine(new InvoiceLineEntity("RETURN_CHARGE", "Frais supplémentaires constatés au retour",
                1, charge, java.util.Map.of("bookingReference", booking.getReference()), 0));
        invoices.saveAndFlush(invoice);
        events.saveAndFlush(new InvoiceEventEntity(invoice, "CREATED", "Invoice created for vehicle return charges."));
        return invoice;
    }

    public java.util.List<InvoiceEntity> listForCustomer(UUID customerId) { return invoices.findByCustomerId(customerId); }
    public InvoiceEntity getForCustomer(UUID id, UUID customerId) {
        // Même réponse 404 pour « inconnue » et « appartient à un autre client » : aucun indice sur l'existence.
        var inv = invoices.findWithLinesById(id).orElseThrow(() -> tn.elitetunisia.common.exception.DomainException.notFound("INVOICE_NOT_FOUND"));
        if (!inv.getCustomerId().equals(customerId)) throw tn.elitetunisia.common.exception.DomainException.notFound("INVOICE_NOT_FOUND");
        return inv;
    }
    public java.util.List<InvoiceEntity> adminList() { return invoices.findAll(); }
    public InvoiceEntity adminGet(UUID id) { return invoices.findWithLinesById(id).orElseThrow(() -> tn.elitetunisia.common.exception.DomainException.notFound("INVOICE_NOT_FOUND")); }

    @Transactional
    public void attachPayment(UUID invoiceId, UUID paymentId) {
        var inv = invoices.findById(invoiceId).orElseThrow(() -> tn.elitetunisia.common.exception.DomainException.notFound("INVOICE_NOT_FOUND"));
        var pay = payments.findById(paymentId).orElseThrow(() -> tn.elitetunisia.common.exception.DomainException.notFound("PAYMENT_NOT_FOUND"));
        // only attach captured payments
        if (pay.getStatus() != tn.elitetunisia.payment.PaymentStatus.CAPTURED) {
            throw tn.elitetunisia.common.exception.DomainException.conflict("PAYMENT_NOT_CAPTURED");
        }
        // create join via native insert to invoice_payments if not exists
        // Postgres supports "on conflict do nothing"; fallback to safe check for H2 or other DBs
        try {
            jdbc.update("insert into invoice_payments(invoice_id,payment_id) values(?,?) on conflict do nothing", inv.getId(), pay.getId());
        } catch (Exception ex) {
            // fallback: insert only if not exists (H2/others)
            Integer exists = jdbc.queryForObject("select count(1) from invoice_payments where invoice_id=? and payment_id=?", Integer.class, inv.getId(), pay.getId());
            if (exists == null || exists == 0) {
                jdbc.update("insert into invoice_payments(invoice_id,payment_id) values(?,?)", inv.getId(), pay.getId());
            }
        }
        events.saveAndFlush(new InvoiceEventEntity(inv, "PAYMENT_ATTACHED", "Payment " + pay.getId() + " attached"));
    }
}
