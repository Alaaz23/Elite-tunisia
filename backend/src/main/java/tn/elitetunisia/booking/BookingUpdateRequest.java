package tn.elitetunisia.booking;
import jakarta.validation.constraints.*;
public record BookingUpdateRequest(@NotBlank @Size(max = 300) String pickupLocation,
        @NotBlank @Size(max = 300) String dropoffLocation, @Size(max = 2000) String internalNote) { }