package tn.elitetunisia.payment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.payment.provider.PaymentProvider;
import tn.elitetunisia.payment.provider.PaymentResult;

/**
 * Réconciliation des tentatives PENDING anciennes et UNKNOWN avec le fournisseur, par clé d'idempotence.
 * La consultation du fournisseur se fait hors transaction (aucun verrou PostgreSQL pendant l'appel externe).
 * Le résultat n'est appliqué qu'après reverrouillage et nouvelle vérification du statut.
 */
@Service
public class PaymentReconciliationService {
    public record Outcome(UUID paymentId, PaymentStatus before, PaymentStatus after, String detail) {}

    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final PaymentProvider provider;
    private final BookingService bookingService;
    private final TransactionTemplate tx;

    public PaymentReconciliationService(PaymentRepository payments, PaymentEventRepository events, PaymentProvider provider,
                                        BookingService bookingService, TransactionTemplate tx) {
        this.payments = payments; this.events = events; this.provider = provider;
        this.bookingService = bookingService; this.tx = tx;
    }

    public List<Outcome> reconcileBatch(Instant pendingBefore, int batchSize) {
        var outcomes = new ArrayList<Outcome>();
        for (UUID id : payments.findReconciliationCandidates(pendingBefore, PageRequest.of(0, batchSize))) {
            try {
                outcomes.add(reconcile(id));
            } catch (RuntimeException ex) {
                outcomes.add(new Outcome(id, null, null, "ERROR:" + ex.getClass().getSimpleName()));
            }
        }
        return outcomes;
    }

    public Outcome reconcile(UUID paymentId) {
        var snapshot = tx.execute(status -> payments.findById(paymentId).orElse(null));
        if (snapshot == null) return new Outcome(paymentId, null, null, "ABSENT");
        if (snapshot.getStatus() != PaymentStatus.PENDING && snapshot.getStatus() != PaymentStatus.UNKNOWN) {
            return new Outcome(paymentId, snapshot.getStatus(), snapshot.getStatus(), "NOT_RECONCILABLE");
        }

        // Preuve demandée hors verrou. Une réponse HTTP seule n'est pas acceptée : seule la consultation par clé fait foi.
        Optional<PaymentResult> proof = lookupSafely(snapshot.getIdempotencyKey());

        return tx.execute(status -> applyUnderLock(paymentId, proof));
    }

    private Optional<PaymentResult> lookupSafely(String key) {
        try {
            return provider.lookup(key);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Outcome applyUnderLock(UUID paymentId, Optional<PaymentResult> proof) {
        UUID bookingId = payments.findBookingIdOf(paymentId).orElse(null);
        if (bookingId != null) bookingService.lockForCommercialOperation(bookingId);
        var payment = payments.findForUpdateSkipLocked(paymentId).orElse(null);
        if (payment == null) return new Outcome(paymentId, null, null, "SKIPPED_LOCKED_OR_ABSENT");

        var before = payment.getStatus();
        if (before != PaymentStatus.PENDING && before != PaymentStatus.UNKNOWN) {
            return new Outcome(paymentId, before, before, "NOT_RECONCILABLE");
        }

        if (proof.isPresent() && proof.get().status() == PaymentStatus.CAPTURED && proof.get().transactionRef() != null) {
            payment.setTransactionRef(proof.get().transactionRef());
            payment.transitionTo(PaymentStatus.CAPTURED);
            payment.setCapturedAt(Instant.now());
            payments.saveAndFlush(payment);
            events.saveAndFlush(new PaymentEventEntity(payment, "RECONCILED_CAPTURED", "Capture confirmée par consultation : " + proof.get().transactionRef()));
            return new Outcome(paymentId, before, PaymentStatus.CAPTURED, "CAPTURED");
        }
        if (proof.isPresent() && proof.get().status() == PaymentStatus.FAILED) {
            payment.transitionTo(PaymentStatus.FAILED);
            payments.saveAndFlush(payment);
            events.saveAndFlush(new PaymentEventEntity(payment, "RECONCILED_FAILED", "Refus confirmé par consultation"));
            return new Outcome(paymentId, before, PaymentStatus.FAILED, "FAILED");
        }

        // Pas de preuve suffisante : le résultat reste inconnu. Aucune libération, aucune nouvelle capture.
        if (before == PaymentStatus.PENDING) {
            payment.transitionTo(PaymentStatus.UNKNOWN);
            payments.saveAndFlush(payment);
            events.saveAndFlush(new PaymentEventEntity(payment, "RECONCILE_UNKNOWN", "Aucune preuve fournisseur : statut UNKNOWN"));
            return new Outcome(paymentId, before, PaymentStatus.UNKNOWN, "INCONCLUSIVE");
        }
        events.saveAndFlush(new PaymentEventEntity(payment, "RECONCILE_INCONCLUSIVE", "Résultat toujours incertain"));
        return new Outcome(paymentId, before, before, "INCONCLUSIVE");
    }

    /** Intervention administrative : seule une tentative UNKNOWN peut être confiée à une revue manuelle. */
    public void requireManualReview(UUID paymentId, String actor, String reason) {
        tx.execute(status -> {
            UUID bookingId = payments.findBookingIdOf(paymentId).orElse(null);
            if (bookingId != null) bookingService.lockForCommercialOperation(bookingId);
            var payment = payments.findForUpdateSkipLocked(paymentId)
                    .orElseThrow(() -> new PaymentException("PAYMENT_LOCKED", "La tentative est verrouillée par un autre traitement."));
            payment.transitionTo(PaymentStatus.MANUAL_REVIEW);
            payments.saveAndFlush(payment);
            events.saveAndFlush(new PaymentEventEntity(payment, "MANUAL_REVIEW", "Par " + actor + " : " + reason));
            return null;
        });
    }
}
