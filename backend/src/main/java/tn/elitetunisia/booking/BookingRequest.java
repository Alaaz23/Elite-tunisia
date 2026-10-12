package tn.elitetunisia.booking;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record BookingRequest(@NotNull UUID vehicleId, @NotNull Instant startDateTime, @NotNull Instant endDateTime,
        @NotBlank @Size(max = 300) String pickupLocation, @NotBlank @Size(max = 300) String dropoffLocation,
        @Size(max = 2000) String customerNote) { }