package tn.elitetunisia.booking;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name = "booking_events")
public class BookingEventEntity {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "actor_id") private UUID actorId;
    @Column(nullable = false, length = 60) private String action;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 30) private BookingStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 30) private BookingStatus toStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected BookingEventEntity() { }
    public BookingEventEntity(UUID bookingId, UUID actorId, String action, BookingStatus from, BookingStatus to, Instant now) {
        id = UUID.randomUUID(); this.bookingId = bookingId; this.actorId = actorId; this.action = action;
        fromStatus = from; toStatus = to; createdAt = now;
    }
    public BookingEventSummary summary() { return new BookingEventSummary(actorId, action, fromStatus, toStatus, createdAt); }
}