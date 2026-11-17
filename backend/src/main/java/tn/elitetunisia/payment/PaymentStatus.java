package tn.elitetunisia.payment;

public enum PaymentStatus {
    PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED, CANCELLED,
    /** Résultat fournisseur inconnu (timeout, réponse perdue, erreur, échec de finalisation). Jamais libéré automatiquement. */
    UNKNOWN,
    /** Intervention administrative requise. Aucune transition automatique. */
    MANUAL_REVIEW;

    /** Transitions techniques autorisées. Les statuts financiers existants (REFUNDED, CANCELLED, AUTHORIZED) ne sont pas modifiés ici. */
    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == CAPTURED || target == FAILED || target == UNKNOWN;
            case UNKNOWN -> target == CAPTURED || target == FAILED || target == MANUAL_REVIEW;
            default -> false;
        };
    }
}
