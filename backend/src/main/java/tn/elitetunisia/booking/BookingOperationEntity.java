package tn.elitetunisia.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "booking_operations")
public class BookingOperationEntity {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(nullable = false, length = 40) private String action;
    @Column(name = "odometer_start") private Integer odometerStart;
    @Column(name = "odometer_end") private Integer odometerEnd;
    @Column(name = "fuel_start") private Integer fuelStart;
    @Column(name = "fuel_end") private Integer fuelEnd;
    @Column(columnDefinition = "text") private String damages;
    @Column(name = "extra_charges", precision = 12, scale = 3) private BigDecimal extraCharges;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected BookingOperationEntity() {}

    public BookingOperationEntity(UUID bookingId, UUID actorId, String action, Integer odometerStart, Integer odometerEnd,
            Integer fuelStart, Integer fuelEnd, String damages, BigDecimal extraCharges) {
        this.id = UUID.randomUUID(); this.bookingId = bookingId; this.actorId = actorId; this.action = action;
        this.odometerStart = odometerStart; this.odometerEnd = odometerEnd; this.fuelStart = fuelStart; this.fuelEnd = fuelEnd;
        this.damages = damages; this.extraCharges = extraCharges;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public Integer getOdometerStart() { return odometerStart; }
    public Integer getOdometerEnd() { return odometerEnd; }
    public Integer getFuelStart() { return fuelStart; }
    public Integer getFuelEnd() { return fuelEnd; }
    public String getDamages() { return damages; }
    public java.math.BigDecimal getExtraCharges() { return extraCharges; }
    public Instant getCreatedAt() { return createdAt; }
}
