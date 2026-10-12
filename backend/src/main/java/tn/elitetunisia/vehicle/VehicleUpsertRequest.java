package tn.elitetunisia.vehicle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleUpsertRequest(
        @NotNull UUID brandId,
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 120) String model,
        @NotNull @Min(1886) @Max(2100) Integer year,
        @NotBlank @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal pricePerDay,
        @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal pricePerWeek,
        @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal priceWithDriver,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @Min(1) @Max(100) Integer passengers,
        @NotNull @Min(1) @Max(20) Integer doors,
        @NotNull TransmissionType transmission,
        @NotNull FuelType fuel,
        boolean withDriverAvailable,
        VehicleStatus status,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> features,
        @Size(max = 30) List<@NotNull @jakarta.validation.Valid VehicleImageRequest> images) {

    @AssertTrue(message = "Un tarif chauffeur positif est obligatoire si le service chauffeur est proposé.")
    @JsonIgnore
    public boolean isDriverPriceValid() {
        return !withDriverAvailable || (priceWithDriver != null && priceWithDriver.compareTo(BigDecimal.ZERO) > 0);
    }

    public boolean hasPrimaryImageConflict() {
        if (images == null) return false;
        return images.stream().filter(VehicleImageRequest::isPrimary).count() > 1;
    }
}