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
@Table(name = "vehicle_images")
public class VehicleImageEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "alt_text", nullable = false, length = 200)
    private String altText;

    @Column(nullable = false)
    private Short position;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VehicleImageEntity() { }

    public VehicleImageEntity(VehicleEntity vehicle, String url, String altText, short position, boolean primary) {
        this.id = UUID.randomUUID();
        this.vehicle = vehicle;
        this.url = url;
        this.altText = altText;
        this.position = position;
        this.primary = primary;
    }

    public UUID getId() { return id; }
    public VehicleEntity getVehicle() { return vehicle; }
    public String getUrl() { return url; }
    public String getAltText() { return altText; }
    public Short getPosition() { return position; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
    public void unsetPrimary() { this.primary = false; }
}