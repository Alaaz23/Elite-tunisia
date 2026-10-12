package tn.elitetunisia.vehicle;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "vehicle_features")
public class VehicleFeatureEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;

    @Column(name = "feature", nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Short position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VehicleFeatureEntity() { }

    public VehicleFeatureEntity(VehicleEntity vehicle, String name, int position) {
        this.id = UUID.randomUUID();
        this.vehicle = vehicle;
        this.name = name;
        this.position = (short) position;
    }

    public UUID getId() { return id; }
    public VehicleEntity getVehicle() { return vehicle; }
    public String getName() { return name; }
    public Short getPosition() { return position; }
    public Instant getCreatedAt() { return createdAt; }
}