package tn.elitetunisia.transfer;

import java.time.Instant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Seuls l'horaire, les adresses et le nombre de passagers sont modifiables ; réservation, véhicule, chauffeur et statut ne le sont pas. */
public record TransferUpdateRequest(
        @NotNull Instant pickupAt,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String dropoffAddress,
        @NotNull @Min(1) @Max(100) Integer passengers) { }
