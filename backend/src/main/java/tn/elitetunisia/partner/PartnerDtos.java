package tn.elitetunisia.partner;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PartnerDtos {
    private PartnerDtos() { }

    public record PartnerRequest(
            @NotNull PartnerType partnerType,
            @NotBlank @Size(max = 160) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 40) String taxIdentifier,
            UUID userId) { }

    public record PartnerSummary(
            UUID id,
            String name,
            PartnerType partnerType,
            String email,
            String phone,
            String address,
            String city,
            String taxIdentifier,
            boolean active,
            UUID userId,
            Instant createdAt,
            Instant updatedAt,
            Instant deactivatedAt) { }

    public record AssignVehicleRequest(
            @NotNull UUID partnerId,
            @NotBlank @Size(max = 500) String reason) { }

    public record AssignmentSummary(
            UUID id,
            UUID vehicleId,
            UUID partnerId,
            String partnerName,
            Instant assignedAt,
            Instant endedAt,
            String reason,
            UUID assignedByUserId) { }

    public record PartnerVehicleSummary(
            UUID id,
            String brandName,
            String model,
            Integer year,
            String status) { }

    public record GuaranteeRequest(
            @NotNull UUID vehicleId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @Size(max = 500) String reason) { }

    public record GuaranteeSummary(
            UUID id,
            UUID vehicleId,
            UUID partnerId,
            String partnerName,
            Instant startsAt,
            Instant endsAt,
            GuaranteeStatus status,
            String reason,
            UUID createdByUserId,
            Instant createdAt,
            UUID withdrawnByUserId,
            Instant withdrawnAt) { }
}
