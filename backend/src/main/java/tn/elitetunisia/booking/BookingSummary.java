package tn.elitetunisia.booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record BookingSummary(UUID id, String reference, UUID customerId, String customerName, UUID vehicleId,
        String vehicleName, Instant startDateTime, Instant endDateTime, String pickupLocation, String dropoffLocation,
        BookingStatus status, int numberOfDays, BigDecimal basePrice, BigDecimal additionalCharges,
        BigDecimal discount, BigDecimal tax, BigDecimal totalAmount, String currency, String customerNote,
        String internalNote, Instant createdAt, Instant updatedAt, Instant confirmedAt, Instant cancelledAt,
        String businessTimeZone, List<BookingEventSummary> history) { }