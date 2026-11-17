package tn.elitetunisia.payment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import tn.elitetunisia.payment.provider.PaymentProvider;
import tn.elitetunisia.payment.provider.PaymentResult;

/**
 * Fournisseur de test contrôlé. Il n'est pas un bean Spring, n'est jamais utilisé en production
 * et ne contacte aucun service externe. Il enregistre chaque appel et chaque capture simulée
 * afin que les tests puissent prouver l'absence de double débit.
 */
final class ControlledPaymentProvider implements PaymentProvider {

    enum Behavior {
        /** Capture confirmée : le fournisseur renvoie CAPTURED avec une référence. */
        CAPTURE,
        /** Refus confirmé : le fournisseur renvoie FAILED. */
        REFUSE,
        /** Délai dépassé : aucune capture, réponse absente (exception). */
        TIMEOUT,
        /** Erreur sans réponse exploitable, aucune capture. */
        ERROR,
        /** Capture effectuée côté fournisseur, mais la réponse est perdue (exception après capture). */
        CAPTURE_RESPONSE_LOST,
        /** Arrêt brutal du processus pendant l'appel (simulé par une Error non interceptée). */
        CRASH,
        /** Capture effectuée côté fournisseur (consultable par clé), puis arrêt brutal avant la réponse. */
        CAPTURE_THEN_CRASH
    }

    static final class SimulatedCrash extends Error {
        SimulatedCrash() { super("Arrêt simulé du processus pendant l'appel fournisseur"); }
    }

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger captures = new AtomicInteger();
    private final Map<String, PaymentResult> ledger = new ConcurrentHashMap<>();
    private volatile Behavior behavior = Behavior.CAPTURE;
    private volatile long delayMillis;
    private volatile Runnable beforeProcess = () -> { };

    ControlledPaymentProvider behavior(Behavior behavior) { this.behavior = behavior; return this; }
    /** Hook exécuté dans le thread appelant, avant la réponse : permet de suspendre l'appel pour un test de concurrence. */
    ControlledPaymentProvider beforeProcess(Runnable hook) { this.beforeProcess = hook; return this; }
    ControlledPaymentProvider delayMillis(long delayMillis) { this.delayMillis = delayMillis; return this; }

    int calls() { return calls.get(); }
    int captures() { return captures.get(); }

    /** Simule une preuve déjà connue du fournisseur pour une clé donnée (ex. refus confirmé hors de l'appel). */
    void seedLedger(String idempotencyKey, PaymentResult result) {
        ledger.put(idempotencyKey, result);
    }

    /** Consultation du statut d'une opération par clé d'idempotence auprès du fournisseur de test. */
    java.util.Optional<PaymentResult> statusOf(String idempotencyKey) {
        return java.util.Optional.ofNullable(ledger.get(idempotencyKey));
    }

    @Override
    public java.util.Optional<PaymentResult> lookup(String idempotencyKey) {
        return statusOf(idempotencyKey);
    }

    @Override
    public PaymentResult process(PaymentEntity payment) {
        calls.incrementAndGet();
        beforeProcess.run();
        if (delayMillis > 0) {
            try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        String ref = "CTRL-" + UUID.randomUUID();
        switch (behavior) {
            case CAPTURE -> {
                captures.incrementAndGet();
                return record(payment, new PaymentResult(PaymentStatus.CAPTURED, ref, "captured"));
            }
            case REFUSE -> {
                return record(payment, new PaymentResult(PaymentStatus.FAILED, null, "refused"));
            }
            case TIMEOUT -> throw new IllegalStateException("Timeout fournisseur (simulé)");
            case ERROR -> throw new IllegalStateException("Erreur fournisseur (simulée)");
            case CAPTURE_RESPONSE_LOST -> {
                captures.incrementAndGet();
                ledger.put(payment.getIdempotencyKey(), new PaymentResult(PaymentStatus.CAPTURED, ref, "captured"));
                throw new IllegalStateException("Réponse fournisseur perdue après capture (simulée)");
            }
            case CRASH -> throw new SimulatedCrash();
            case CAPTURE_THEN_CRASH -> {
                captures.incrementAndGet();
                ledger.put(payment.getIdempotencyKey(), new PaymentResult(PaymentStatus.CAPTURED, ref, "captured"));
                throw new SimulatedCrash();
            }
            default -> throw new IllegalStateException("Comportement inconnu");
        }
    }

    private PaymentResult record(PaymentEntity payment, PaymentResult result) {
        ledger.put(payment.getIdempotencyKey(), result);
        return result;
    }
}
