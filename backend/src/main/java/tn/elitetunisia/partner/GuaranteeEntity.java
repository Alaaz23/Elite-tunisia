package tn.elitetunisia.partner;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.vehicle.VehicleEntity;

/**
 * Garantie véhicule-partenaire. {@code partner} est le partenaire ayant engagé le véhicule au moment de la création :
 * il reste immuable, même si le véhicule change ensuite de partenaire.
 */
@Entity
@Table(name = "vehicle_guarantees")
public class GuaranteeEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    private VehicleEntity vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private PartnerEntity partner;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuaranteeStatus status;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
    private UserEntity createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawn_by_user_id")
    private UserEntity withdrawnBy;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected GuaranteeEntity() { }

    public GuaranteeEntity(VehicleEntity vehicle, PartnerEntity partner, Instant startsAt, Instant endsAt,
            String reason, UserEntity createdBy, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.vehicle = vehicle;
        this.partner = partner;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = GuaranteeStatus.ACTIVE;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void withdraw(UserEntity actor, Instant at) {
        this.status = GuaranteeStatus.WITHDRAWN;
        this.withdrawnBy = actor;
        this.withdrawnAt = at;
    }

    public UUID getId() { return id; }
    public VehicleEntity getVehicle() { return vehicle; }
    public PartnerEntity getPartner() { return partner; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public GuaranteeStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public UserEntity getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public UserEntity getWithdrawnBy() { return withdrawnBy; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
