package tn.elitetunisia.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2000) String description,
        Boolean active) { }