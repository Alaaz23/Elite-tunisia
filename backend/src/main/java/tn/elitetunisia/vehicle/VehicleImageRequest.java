package tn.elitetunisia.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleImageRequest(
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^(https?://[^\\s]+|/api/files/[A-Za-z0-9._-]+)$") String url,
        @NotBlank @Size(max = 200) String altText,
        @Min(0) short position,
        boolean isPrimary) { }