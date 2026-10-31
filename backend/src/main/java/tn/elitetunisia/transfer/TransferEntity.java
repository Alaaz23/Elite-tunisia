package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "transfers")
public class TransferEntity {
    @Id
    @GeneratedValue
    private UUID id;
    @Column(name = "booking_id")
    private UUID bookingId;
    private Instant pickupAt;
    private String pickupAddress;
    private String dropoffAddress;
    private Integer passengers;
    private UUID driverId;
    private UUID vehicleId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status = TransferStatus.PENDING;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TransferEntity() {}

    /** Le véhicule est toujours celui de la réservation ; il n'est jamais fourni par le client HTTP. */
    public static TransferEntity forBooking(UUID bookingId, UUID vehicleId, UUID driverId, Instant pickupAt,
            String pickupAddress, String dropoffAddress, Integer passengers) {
        var t = new TransferEntity();
        t.bookingId = bookingId; t.vehicleId = vehicleId; t.driverId = driverId; t.pickupAt = pickupAt;
        t.pickupAddress = pickupAddress; t.dropoffAddress = dropoffAddress; t.passengers = passengers;
        return t;
    }

    public void reschedule(Instant pickupAt, String pickupAddress, String dropoffAddress, Integer passengers) {
        this.pickupAt = pickupAt; this.pickupAddress = pickupAddress; this.dropoffAddress = dropoffAddress; this.passengers = passengers;
    }

    public void transitionTo(TransferStatus target) { this.status = target; }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public Instant getPickupAt() { return pickupAt; }
    public void setPickupAt(Instant t) { this.pickupAt = t; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String a) { this.pickupAddress = a; }
    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String a) { this.dropoffAddress = a; }
    public Integer getPassengers() { return passengers; }
    public void setPassengers(Integer p) { this.passengers = p; }
    public UUID getDriverId() { return driverId; }
    public UUID getVehicleId() { return vehicleId; }
    public TransferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
