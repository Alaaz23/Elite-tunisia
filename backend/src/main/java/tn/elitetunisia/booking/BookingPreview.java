package tn.elitetunisia.booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record BookingPreview(UUID vehicleId, String vehicleName, Instant startDateTime, Instant endDateTime,
        int numberOfDays, BigDecimal basePrice, BigDecimal additionalCharges, BigDecimal discount, BigDecimal tax,
        BigDecimal totalAmount, String currency, String businessTimeZone) { }