package tn.elitetunisia.partner;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.vehicle.VehicleEntity;

/** Historique append-only des rattachements véhicule-partenaire (voir trigger V23). */
@Entity
@Table(name = "vehicle_partner_assignments")
public class VehiclePartnerAssignmentEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    private VehicleEntity vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private PartnerEntity partner;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_user_id", nullable = false, updatable = false)
    private UserEntity assignedBy;

    protected VehiclePartnerAssignmentEntity() { }

    public VehiclePartnerAssignmentEntity(VehicleEntity vehicle, PartnerEntity partner, String reason,
            UserEntity assignedBy, Instant assignedAt) {
        this.id = UUID.randomUUID();
        this.vehicle = vehicle;
        this.partner = partner;
        this.reason = reason;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public void close(Instant at) { this.endedAt = at; }

    public UUID getId() { return id; }
    public VehicleEntity getVehicle() { return vehicle; }
    public PartnerEntity getPartner() { return partner; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getReason() { return reason; }
    public UserEntity getAssignedBy() { return assignedBy; }
}
