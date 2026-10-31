package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création d'un transfert. Ni l'identifiant, ni le statut, ni le véhicule ne peuvent être imposés par le client :
 * le véhicule vient de la réservation et le statut initial est toujours PENDING.
 */
public record TransferRequest(
        @NotNull UUID bookingId,
        @NotNull Instant pickupAt,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String dropoffAddress,
        @NotNull @Min(1) @Max(100) Integer passengers,
        UUID driverId) { }
