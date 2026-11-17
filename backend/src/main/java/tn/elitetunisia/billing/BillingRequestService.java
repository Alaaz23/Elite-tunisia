package tn.elitetunisia.billing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.invoice.InvoiceEntity;
import tn.elitetunisia.invoice.InvoiceService;

/**
 * Outbox de facturation principale après retour.
 * <ul>
 *   <li>{@link #enqueueMainInvoice} s'exécute dans la transaction du retour : retour et demande sont atomiques.</li>
 *   <li>{@link #process} génère la facture dans sa propre transaction (Vehicle → Booking → demande → Quote).
 *       Un échec n'annule jamais le retour ; il est enregistré dans une transaction distincte avec le backoff.</li>
 *   <li>Après {@code maxAttempts} échecs, la demande passe à DEAD. Seule une reprise administrative, motivée et auditée,
 *       la remet en file ({@link #retryDeadRequest}).</li>
 * </ul>
 * Idempotence : UNIQUE (booking_id, kind) côté base, verrou de la demande, relecture du statut et de l'échéance
 * après verrou, et {@link InvoiceService#createMainInvoiceAfterReturn} qui renvoie la facture principale existante.
 */
@Service
public class BillingRequestService {
    private static final Logger LOG = LoggerFactory.getLogger(BillingRequestService.class);

    public enum Outcome { DONE, RETRY, DEAD, SKIPPED }

    public record HistoryLine(Instant occurredAt, String event, Integer attemptNo, String error, UUID actorId, String reason) { }

    private final BillingRequestRepository requests;
    private final BillingRequestHistoryRepository historyRows;
    private final BookingService bookingService;
    private final InvoiceService invoices;
    private final TransactionTemplate generation;
    private final TransactionTemplate failureRecording;
    private final int maxAttempts;

    public BillingRequestService(BillingRequestRepository requests, BillingRequestHistoryRepository historyRows,
            BookingService bookingService, InvoiceService invoices, PlatformTransactionManager transactions,
            @Value("${app.billing.max-attempts:5}") int maxAttempts) {
        this.requests = requests; this.historyRows = historyRows; this.bookingService = bookingService; this.invoices = invoices;
        this.generation = new TransactionTemplate(transactions);
        this.failureRecording = new TransactionTemplate(transactions);
        this.failureRecording.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxAttempts = maxAttempts;
    }

    /** Appelé dans la transaction du retour. Idempotent : renvoie la demande existante si elle existe. */
    @Transactional
    public BillingRequestEntity enqueueMainInvoice(UUID bookingId, UUID requestedBy) {
        var existing = requests.findByBookingIdAndKind(bookingId, BillingRequestEntity.KIND_MAIN_INVOICE);
        if (existing.isPresent()) return existing.get();
        Instant now = Instant.now();
        var created = requests.saveAndFlush(new BillingRequestEntity(bookingId, BillingRequestEntity.KIND_MAIN_INVOICE, requestedBy, now));
        record(created.getId(), "ENQUEUED", null, null, requestedBy, null, now);
        return created;
    }

    /** Traite une demande échue. Ne lève pas d'exception métier : le résultat est retourné et tracé. */
    public Outcome process(UUID requestId) {
        var observed = requests.findById(requestId).orElse(null);
        if (observed == null || !observed.isDue(Instant.now())) return Outcome.SKIPPED;
        try {
            return generation.execute(status -> generate(requestId, observed.getBookingId()));
        } catch (RuntimeException ex) {
            LOG.warn("Main invoice generation failed for billing request {}", requestId, ex);
            return failureRecording.execute(status -> recordFailure(requestId, ex));
        }
    }

    /** Demandes échues, à reprendre après redémarrage ou après un délai de backoff. */
    public List<UUID> dueIds() {
        return requests.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        BillingRequestEntity.Status.PENDING, Instant.now()).stream()
                .map(BillingRequestEntity::getId).toList();
    }

    public org.springframework.data.domain.Page<BillingRequestEntity> listByStatus(BillingRequestEntity.Status status,
            org.springframework.data.domain.Pageable pageable) {
        return requests.findByStatus(status, pageable);
    }

    public BillingRequestEntity get(UUID requestId) {
        return requests.findById(requestId).orElseThrow(() -> DomainException.notFound("BILLING_REQUEST_NOT_FOUND"));
    }

    public List<HistoryLine> history(UUID requestId) {
        get(requestId);
        return historyRows.findByRequestIdOrderByIdAsc(requestId).stream()
                .map(h -> new HistoryLine(h.getOccurredAt(), h.getEvent(), h.getAttemptNo(), h.getError(),
                        h.getActorId(), h.getReason()))
                .toList();
    }

    /**
     * Reprise administrative d'une demande DEAD. Le motif et l'acteur sont obligatoires et tracés.
     * L'historique des tentatives est conservé ; seul le compteur du cycle est remis à zéro.
     */
    @Transactional
    public BillingRequestEntity retryDeadRequest(UUID requestId, UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) throw DomainException.invalid("BILLING_RETRY_REASON_REQUIRED");
        var request = requests.findLockedById(requestId).orElseThrow(() -> DomainException.notFound("BILLING_REQUEST_NOT_FOUND"));
        if (request.getStatus() != BillingRequestEntity.Status.DEAD) throw DomainException.conflict("BILLING_REQUEST_NOT_DEAD");
        Instant now = Instant.now();
        request.retryFromDead(now);
        record(requestId, "MANUAL_RETRY", request.getAttempts(), null, actorId, reason.trim(), now);
        return request;
    }

    private Outcome generate(UUID requestId, UUID bookingId) {
        // Ordre global : Vehicle → Booking, puis la demande, puis Quote (verrouillé par InvoiceService).
        BookingEntity booking = bookingService.lockForOperation(bookingId);
        var request = requests.findLockedById(requestId).orElseThrow();
        // Relecture après verrou : un autre worker a pu terminer la demande ou la replanifier.
        Instant now = Instant.now();
        if (!request.isDue(now)) return Outcome.SKIPPED;
        InvoiceEntity invoice = invoices.createMainInvoiceAfterReturn(booking, request.getRequestedBy());
        request.markDone(invoice.getId(), now);
        record(requestId, "DONE", request.getAttempts() + 1, null, null, null, now);
        return Outcome.DONE;
    }

    private Outcome recordFailure(UUID requestId, RuntimeException ex) {
        var request = requests.findLockedById(requestId).orElseThrow();
        if (request.getStatus() != BillingRequestEntity.Status.PENDING) return Outcome.SKIPPED;
        Instant now = Instant.now();
        String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        request.recordFailure(error, now, maxAttempts);
        boolean dead = request.getStatus() == BillingRequestEntity.Status.DEAD;
        record(requestId, dead ? "DEAD" : "ATTEMPT_FAILED", request.getAttempts(), request.getLastError(), null, null, now);
        return dead ? Outcome.DEAD : Outcome.RETRY;
    }

    private void record(UUID requestId, String event, Integer attemptNo, String error, UUID actorId, String reason, Instant now) {
        historyRows.save(new BillingRequestHistoryEntity(requestId, now, event, attemptNo, error, actorId, reason));
    }
}
