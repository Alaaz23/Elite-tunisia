package tn.elitetunisia.booking;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.quote.QuoteRepository;
import tn.elitetunisia.vehicle.VehicleRepository;

public class BookingServiceUnitLogicTest {

    @Mock BookingRepository bookings;
    @Mock VehicleRepository vehicles;
    @Mock CustomerRepository customers;
    @Mock BookingEventRepository events;
    @Mock AuditService audit;
    @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Mock tn.elitetunisia.common.pricing.RentalPricingService pricing;
    @Mock QuoteRepository quotes;
    @Mock BookingOperationRepository operations;

    BookingService svc;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        svc = new BookingService(bookings, vehicles, customers, events, audit, jdbc, "UTC", pricing, quotes, operations);
    }

    @Test
    void pendingDoesNotCountAsBlocking() {
        UUID vehicleId = UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant end = start.plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        // bookings.overlaps should be called with BLOCKING statuses only; simulate 0
        when(bookings.overlaps(eq(vehicleId), eq(start), eq(end), anyList(), isNull())).thenReturn(Long.valueOf(0L));
        // vehicle lookup mocked and set to rentable (AVAILABLE + active brand/category)
        tn.elitetunisia.vehicle.VehicleEntity vehicle = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(vehicle.getStatus()).thenReturn(tn.elitetunisia.vehicle.VehicleStatus.AVAILABLE);
        tn.elitetunisia.brand.BrandEntity brand = mock(tn.elitetunisia.brand.BrandEntity.class);
        when(brand.isActive()).thenReturn(true);
        tn.elitetunisia.category.CategoryEntity category = mock(tn.elitetunisia.category.CategoryEntity.class);
        when(category.isActive()).thenReturn(true);
        when(vehicle.getBrand()).thenReturn(brand);
        when(vehicle.getCategory()).thenReturn(category);
        when(vehicle.getId()).thenReturn(vehicleId);
        when(vehicles.findById(any())).thenReturn(java.util.Optional.of(vehicle));
        AvailabilityResponse r = svc.availability(vehicleId, start, end);
        assertTrue(r.available());
    }

    @Test
    void validateDatesRejectsPastOrInvalid() {
        Instant now = Instant.now();
        Instant start = now.minusSeconds(10);
        Instant end = now.plusSeconds(100);
        assertThrows(BookingException.class, () -> svc.validateDates(start, end));
        // end before start
        assertThrows(BookingException.class, () -> svc.validateDates(end, start));
    }
}
