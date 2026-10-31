package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID bookingId, UUID vehicleId, UUID driverId, Instant pickupAt,
        String pickupAddress, String dropoffAddress, Integer passengers, TransferStatus status,
        Instant createdAt, Instant updatedAt) {
    public static TransferResponse from(TransferEntity t) {
        return new TransferResponse(t.getId(), t.getBookingId(), t.getVehicleId(), t.getDriverId(), t.getPickupAt(),
                t.getPickupAddress(), t.getDropoffAddress(), t.getPassengers(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
