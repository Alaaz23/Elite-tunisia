package tn.elitetunisia.driver;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "booking_driver_assignments")
public class BookingDriverAssignmentEntity {
    @Id @GeneratedValue private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "driver_id", nullable = false) private UUID driverId;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;

    protected BookingDriverAssignmentEntity() {}
    public BookingDriverAssignmentEntity(UUID bookingId, UUID driverId, Instant startsAt, Instant endsAt) {
        this.bookingId = bookingId; this.driverId = driverId; this.startsAt = startsAt; this.endsAt = endsAt;
    }
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getDriverId() { return driverId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
}
