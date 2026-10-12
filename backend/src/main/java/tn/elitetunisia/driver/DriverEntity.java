package tn.elitetunisia.driver;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "drivers")
public class DriverEntity {
    @Id
    @GeneratedValue
    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean active = true;
    private Instant createdAt = Instant.now();
    private String licenseNumber;
    private Instant licenseExpiry;
    private boolean certified = false;
    private String notes;

    public DriverEntity() {}
    public DriverEntity(String firstName, String lastName, String phone) {
        this.firstName = firstName; this.lastName = lastName; this.phone = phone;
    }
    public String getLicenseNumber() { return licenseNumber; }
    public Instant getLicenseExpiry() { return licenseExpiry; }
    public boolean isCertified() { return certified; }
    public String getNotes() { return notes; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public void setLicenseExpiry(Instant licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public void setCertified(boolean certified) { this.certified = certified; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { this.active = a; }
    public Instant getCreatedAt() { return createdAt; }
}
