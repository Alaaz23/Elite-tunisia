package tn.elitetunisia.driver;

import java.time.Instant;
import java.util.UUID;

/** Représentation publique d'un chauffeur pour l'administration ; l'entité JPA n'est jamais sérialisée directement. */
public record DriverResponse(UUID id, String firstName, String lastName, String phone, String licenseNumber,
        Instant licenseExpiry, boolean certified, String notes, boolean active, Instant createdAt) {
    public static DriverResponse from(DriverEntity d) {
        return new DriverResponse(d.getId(), d.getFirstName(), d.getLastName(), d.getPhone(), d.getLicenseNumber(),
                d.getLicenseExpiry(), d.isCertified(), d.getNotes(), d.isActive(), d.getCreatedAt());
    }
}
