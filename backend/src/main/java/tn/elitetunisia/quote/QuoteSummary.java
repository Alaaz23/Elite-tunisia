package tn.elitetunisia.quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record QuoteSummary(UUID id, String reference, UUID bookingId, String bookingReference, tn.elitetunisia.booking.BookingStatus bookingStatus, UUID customerId,
        String customerName, UUID vehicleId, String vehicleName, Instant startDateTime, Instant endDateTime,
        String pickupLocation, String dropoffLocation, int version, QuoteStatus status, String currency,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal totalAmount,
        Instant validUntil, String customerNote, String internalNote, Instant createdAt, Instant updatedAt,
        Instant sentAt, Instant acceptedAt, Instant rejectedAt, String businessTimeZone,
        List<QuoteLineSummary> lines, List<QuoteEventSummary> history) { }