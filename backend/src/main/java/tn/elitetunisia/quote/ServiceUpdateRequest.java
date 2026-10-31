package tn.elitetunisia.quote;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Modification d'un service : ni la devise ni l'état actif ne passent par ici (voir activation / désactivation). */
public record ServiceUpdateRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotNull @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal price) { }
