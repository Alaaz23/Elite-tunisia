package tn.elitetunisia.driver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.booking.BookingRepository;

public class DriverAssignmentBusinessRulesTest {
    DriverRepository drivers = mock(DriverRepository.class);
    BookingRepository bookings = mock(BookingRepository.class);
    BookingDriverAssignmentRepository assignments = mock(BookingDriverAssignmentRepository.class);
    DriverAssignmentService svc;

    @BeforeEach
    void init() {
        svc = new DriverAssignmentService(drivers, bookings, assignments, null);
    }

    BookingEntity makeBooking(UUID id) {
        var b = mock(BookingEntity.class);
        when(b.getId()).thenReturn(id);
        when(b.getStatus()).thenReturn(BookingStatus.PENDING);
        when(b.getStartDateTime()).thenReturn(Instant.now().plusSeconds(3600));
        when(b.getEndDateTime()).thenReturn(Instant.now().plusSeconds(7200));
        return b;
    }

    @Test
    public void cannotAssignExpiredLicense() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        var b = makeBooking(bookingId);
        when(bookings.findById(eq(bookingId))).thenReturn(Optional.of(b));
        var d = new DriverEntity("A","B","+216");
        d.setLicenseExpiry(Instant.now().minusSeconds(10));
        when(drivers.findById(eq(driverId))).thenReturn(Optional.of(d));

        var ex = assertThrows(RuntimeException.class, () -> svc.assignDriver(bookingId, driverId, false));
        assertEquals("DRIVER_LICENSE_EXPIRED", ex.getMessage());
    }

    @Test
    public void cannotAssignWhenCertificationRequiredButMissing() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        var b = makeBooking(bookingId);
        when(bookings.findById(eq(bookingId))).thenReturn(Optional.of(b));
        var d = new DriverEntity("A","B","+216");
        d.setLicenseExpiry(Instant.now().plusSeconds(3600));
        d.setCertified(false);
        when(drivers.findById(eq(driverId))).thenReturn(Optional.of(d));

        var ex = assertThrows(RuntimeException.class, () -> svc.assignDriver(bookingId, driverId, true));
        assertEquals("DRIVER_NOT_CERTIFIED", ex.getMessage());
    }

    @Test
    public void successfulAssignmentWhenValid() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        var b = makeBooking(bookingId);
        when(bookings.findById(eq(bookingId))).thenReturn(Optional.of(b));
        var d = new DriverEntity("A","B","+216");
        d.setLicenseExpiry(Instant.now().plusSeconds(3600));
        d.setCertified(true);
        when(drivers.findById(eq(driverId))).thenReturn(Optional.of(d));
        when(assignments.overlaps(eq(driverId), any(), any(), eq(bookingId))).thenReturn(0L);
        when(assignments.findByBookingId(eq(bookingId))).thenReturn(null);
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var a = svc.assignDriver(bookingId, driverId, true);
        assertNotNull(a);
        assertEquals(driverId, a.getDriverId());
    }
}
