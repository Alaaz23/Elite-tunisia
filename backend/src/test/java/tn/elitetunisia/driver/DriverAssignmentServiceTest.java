package tn.elitetunisia.driver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;

public class DriverAssignmentServiceTest {
    BookingRepository bookings;
    DriverRepository drivers;
    BookingDriverAssignmentRepository assignments;
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    DriverAssignmentService svc;

    @BeforeEach
    void init() {
        bookings = mock(BookingRepository.class);
        drivers = mock(DriverRepository.class);
        assignments = mock(BookingDriverAssignmentRepository.class);
        jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        svc = new DriverAssignmentService(drivers, bookings, assignments, jdbc);
    }

    @Test
    void assignAndUnassignHappyPath() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.PENDING);
        when(b.getStartDateTime()).thenReturn(Instant.now().plusSeconds(3600));
        when(b.getEndDateTime()).thenReturn(Instant.now().plusSeconds(7200));
        DriverEntity d = new DriverEntity("A","B","+216...");
        // set id via reflection not necessary for logic
        when(drivers.findById(driverId)).thenReturn(Optional.of(d));
        when(assignments.overlaps(eq(driverId), any(), any(), eq(bookingId))).thenReturn(0L);
        when(assignments.findByBookingId(bookingId)).thenReturn(null);
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));

        var created = svc.assignDriver(bookingId, driverId);
        assertNotNull(created);
        verify(assignments).save(any());

        when(assignments.findByBookingId(bookingId)).thenReturn(created);
        svc.unassignDriver(bookingId);
        verify(assignments).delete(created);
    }

    @Test
    void assignFailsWhenOverlap() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.PENDING);
        when(b.getStartDateTime()).thenReturn(Instant.now().plusSeconds(3600));
        when(b.getEndDateTime()).thenReturn(Instant.now().plusSeconds(7200));
        DriverEntity d = new DriverEntity("A","B","+216...");
        when(drivers.findById(driverId)).thenReturn(Optional.of(d));
        when(assignments.overlaps(eq(driverId), any(), any(), eq(bookingId))).thenReturn(1L);

        var ex = assertThrows(RuntimeException.class, () -> svc.assignDriver(bookingId, driverId));
        assertEquals("DRIVER_NOT_AVAILABLE", ex.getMessage());
    }

    @Test
    void assignFailsWhenBookingStatusInvalid() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.CANCELLED);
        var ex = assertThrows(RuntimeException.class, () -> svc.assignDriver(bookingId, driverId));
        assertEquals("BOOKING_STATUS_NO_DRIVER", ex.getMessage());
    }
}
