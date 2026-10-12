package tn.elitetunisia.booking;

public enum BookingStatus {
    PENDING, CONFIRMED, REJECTED, CANCELLED, IN_PROGRESS, COMPLETED;

    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == REJECTED || target == CANCELLED;
            case CONFIRMED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED;
            default -> false;
        };
    }
}