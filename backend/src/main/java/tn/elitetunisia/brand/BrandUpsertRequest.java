package tn.elitetunisia.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BrandUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2000) String description,
        @Size(max = 1000) @Pattern(regexp = "^(https?://[^\\s]+|/api/files/[A-Za-z0-9._-]+)$", message = "Le logo doit être une URL http(s) ou un fichier Elite Tunisia.") String logoUrl,
        Boolean active) { }