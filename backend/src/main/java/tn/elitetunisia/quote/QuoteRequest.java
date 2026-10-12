package tn.elitetunisia.quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record QuoteRequest(@NotNull Instant validUntil, boolean withDriver,
        @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal discountAmount,
        @Size(max = 2000) String customerNote, @Size(max = 2000) String internalNote,
        @Size(max = 30) List<@Valid ServiceSelection> services) {
    public record ServiceSelection(@NotNull UUID serviceId, @Min(1) @Max(1000) int quantity) { }
}