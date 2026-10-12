package tn.elitetunisia.booking;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.vehicle.VehicleRepository;

public class BookingServiceIdempotenceTest {
    BookingRepository bookings;
    VehicleRepository vehicles;
    BookingEventRepository events;
    AuditService audit;
    JdbcTemplate jdbc;
    tn.elitetunisia.common.pricing.RentalPricingService pricing;
    tn.elitetunisia.quote.QuoteRepository quotes;
    BookingService service;

    @BeforeEach
    public void setup() {
        bookings = mock(BookingRepository.class);
        vehicles = mock(VehicleRepository.class);
        events = mock(BookingEventRepository.class);
        audit = mock(AuditService.class);
        jdbc = mock(JdbcTemplate.class);
        pricing = mock(tn.elitetunisia.common.pricing.RentalPricingService.class);
        quotes = mock(tn.elitetunisia.quote.QuoteRepository.class);
        service = new BookingService(bookings, vehicles, mock(tn.elitetunisia.customer.CustomerRepository.class), events, audit, jdbc, "UTC", pricing, quotes);
    }

    @Test
    public void confirmIsIdempotentWhenAlreadyConfirmed() {
        tn.elitetunisia.vehicle.VehicleEntity vehicle = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        UUID vehicleId = UUID.randomUUID();
        when(vehicle.getId()).thenReturn(vehicleId);
        var brand = mock(tn.elitetunisia.brand.BrandEntity.class);
        when(vehicle.getBrand()).thenReturn(brand);
        when(brand.getName()).thenReturn("Test");
        when(vehicle.getModel()).thenReturn("Touring");
        when(vehicle.getPricePerDay()).thenReturn(new java.math.BigDecimal("100.123"));
        var customer = mock(tn.elitetunisia.customer.CustomerEntity.class);
        UUID customerId = UUID.randomUUID();
        when(customer.getId()).thenReturn(customerId);
        when(customer.getFirstName()).thenReturn("Booking");
        when(customer.getLastName()).thenReturn("Client");
        Instant start = Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var amount = new java.math.BigDecimal("100.123");
        var request = new BookingRequest(vehicleId, start, start.plusSeconds(86400), "Tunis", "Tunis", null);
        var preview = new BookingPreview(vehicleId, "Test Touring", start, request.endDateTime(), 1,
            amount, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, amount, "TND", "UTC");
        BookingEntity booking = new BookingEntity("ET-TEST-IDEMPOTENCE", customer, vehicle, request, preview);
        Instant confirmedAt = Instant.now();
        booking.transition(BookingStatus.CONFIRMED, confirmedAt);
        UUID id = booking.getId();
        when(bookings.findById(id)).thenReturn(Optional.of(booking));
        when(bookings.findLocked(id)).thenReturn(Optional.of(booking));
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        // inject a mock EntityManager to avoid NPE when refresh is called
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        try { java.lang.reflect.Field f = BookingService.class.getDeclaredField("entityManager"); f.setAccessible(true); f.set(service, em); } catch (Exception e) { throw new RuntimeException(e); }
        // call transition to CONFIRMED should return summary and not call events.save
        var summary = service.transition(id, BookingStatus.CONFIRMED, UUID.randomUUID(), "127.0.0.1");
        var repeated = service.transition(id, BookingStatus.CONFIRMED, UUID.randomUUID(), "127.0.0.1");
        assertEquals(summary, repeated);
        assertEquals(BookingStatus.CONFIRMED, summary.status());
        assertEquals(customerId, summary.customerId());
        assertEquals(amount, summary.basePrice());
        assertEquals(amount, summary.totalAmount());
        assertEquals(confirmedAt, summary.confirmedAt());
        assertTrue(summary.history().isEmpty());
        verify(events, times(2)).findByBookingIdOrderByCreatedAtAsc(id);
        verify(events, never()).saveAndFlush(any());
        verify(bookings, never()).flush();
        verifyNoInteractions(audit, quotes, pricing);
    }
}
