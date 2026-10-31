package tn.elitetunisia.quote;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Création d'un service ; la devise est fixée ici et ne pourra plus être modifiée. */
public record ServiceCreateRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotNull @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal price,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency) { }
