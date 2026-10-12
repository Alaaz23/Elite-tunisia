package tn.elitetunisia.booking;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.vehicle.VehicleRepository;

public class BookingServiceConfirmTests {
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
        // inject mock EntityManager
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        try { java.lang.reflect.Field f = BookingService.class.getDeclaredField("entityManager"); f.setAccessible(true); f.set(service, em); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    public void confirmNormal() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        var vehicle = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        var customer = mock(tn.elitetunisia.customer.CustomerEntity.class);
        var brand = mock(tn.elitetunisia.brand.BrandEntity.class);
        var category = mock(tn.elitetunisia.category.CategoryEntity.class);
        when(booking.getCustomer()).thenReturn(customer);
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(vehicle.getBrand()).thenReturn(brand);
        when(vehicle.getCategory()).thenReturn(category);
        when(brand.isActive()).thenReturn(true);
        when(category.isActive()).thenReturn(true);
        when(vehicle.getStatus()).thenReturn(tn.elitetunisia.vehicle.VehicleStatus.AVAILABLE);
        UUID vehicleId = UUID.randomUUID();
        when(vehicle.getId()).thenReturn(vehicleId);
        when(booking.getVehicle()).thenReturn(vehicle);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        var start = java.time.Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(booking.getStartDateTime()).thenReturn(start);
        when(booking.getEndDateTime()).thenReturn(start.plusSeconds(3600));
        when(bookings.findLocked(id)).thenReturn(Optional.of(booking));
        when(bookings.findById(id)).thenReturn(Optional.of(booking));
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookings.overlaps(vehicleId, booking.getStartDateTime(), booking.getEndDateTime(), BookingService.BLOCKING, id)).thenReturn(0L);
        tn.elitetunisia.quote.QuoteEntity quote = mock(tn.elitetunisia.quote.QuoteEntity.class);
        when(quotes.findFirstByBookingIdOrderByVersionDesc(id)).thenReturn(Optional.of(quote));
        when(quote.getStatus()).thenReturn(tn.elitetunisia.quote.QuoteStatus.ACCEPTED);
        when(quote.coherentWith(booking)).thenReturn(true);
        // perform
        var summary = service.transition(id, BookingStatus.CONFIRMED, UUID.randomUUID(), "127.0.0.1");
        assertNotNull(summary);
        verify(booking).applyCommercialPrice(quote);
        verify(booking).transition(eq(BookingStatus.CONFIRMED), any());
        verify(events).saveAndFlush(any());
        verify(audit).recordAudit(any(), eq("BOOKING_CONFIRMED"), eq("BOOKING"), any(), eq("127.0.0.1"), eq("SUCCESS"));
    }

    @Test
    public void confirmFailsWhenVehicleUnavailable() {
        UUID id = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        var vehicle = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        UUID vehicleId = UUID.randomUUID();
        when(vehicle.getId()).thenReturn(vehicleId);
        when(booking.getVehicle()).thenReturn(vehicle);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        var start = java.time.Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(booking.getStartDateTime()).thenReturn(start);
        when(booking.getEndDateTime()).thenReturn(start.plusSeconds(3600));
        when(bookings.findLocked(id)).thenReturn(Optional.of(booking));
        when(bookings.findById(id)).thenReturn(Optional.of(booking));
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookings.overlaps(vehicleId, booking.getStartDateTime(), booking.getEndDateTime(), BookingService.BLOCKING, id)).thenReturn(1L);
        assertEquals("VEHICLE_NOT_AVAILABLE", assertThrows(BookingException.class, () -> service.transition(id, BookingStatus.CONFIRMED, UUID.randomUUID(), "127.0.0.1")).getCode());
        verifyNoInteractions(events, audit);
    }
}
