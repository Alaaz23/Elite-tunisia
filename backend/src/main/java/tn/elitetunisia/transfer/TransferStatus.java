package tn.elitetunisia.transfer;

public enum TransferStatus {
    PENDING, CONFIRMED, COMPLETED, CANCELLED;

    public boolean canTransitionTo(TransferStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Un transfert n'est modifiable (horaire, adresses, passagers) que tant qu'il n'est pas terminé ou annulé. */
    public boolean editable() { return this == PENDING || this == CONFIRMED; }

    /** Seuls les transferts actifs occupent le chauffeur. */
    public boolean occupiesDriver() { return this == PENDING || this == CONFIRMED; }
}
