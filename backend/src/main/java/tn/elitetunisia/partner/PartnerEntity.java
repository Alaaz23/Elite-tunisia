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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.elitetunisia.auth.entity.UserEntity;

@Entity
@Table(name = "partners")
public class PartnerEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 40)
    private PartnerType partnerType;

    @Column(length = 254)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "tax_identifier", length = 40)
    private String taxIdentifier;

    @Column(nullable = false)
    private boolean active;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private UserEntity user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    protected PartnerEntity() { }

    public PartnerEntity(String name, PartnerType partnerType) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.partnerType = partnerType;
        this.active = true;
    }

    public void update(String name, PartnerType partnerType, String email, String phone, String address, String city,
            String taxIdentifier, UserEntity user) {
        this.name = name;
        this.partnerType = partnerType;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.taxIdentifier = taxIdentifier;
        this.user = user;
    }

    public void activate() {
        this.active = true;
        this.deactivatedAt = null;
    }

    public void deactivate(Instant at) {
        this.active = false;
        this.deactivatedAt = at;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public PartnerType getPartnerType() { return partnerType; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getTaxIdentifier() { return taxIdentifier; }
    public boolean isActive() { return active; }
    public UserEntity getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
}
