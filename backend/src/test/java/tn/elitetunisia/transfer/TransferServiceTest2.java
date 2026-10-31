package tn.elitetunisia.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.driver.BookingDriverAssignmentEntity;
import tn.elitetunisia.driver.BookingDriverAssignmentRepository;

public class TransferServiceTest2 {
    TransferRepository repo;
    BookingRepository bookings;
    BookingDriverAssignmentRepository assignments;
    tn.elitetunisia.driver.DriverRepository drivers;
    tn.elitetunisia.common.audit.AuditService audit;
    TransferService svc;

    @BeforeEach
    void init() {
        repo = mock(TransferRepository.class);
        bookings = mock(BookingRepository.class);
        assignments = mock(BookingDriverAssignmentRepository.class);
        drivers = mock(tn.elitetunisia.driver.DriverRepository.class);
        audit = mock(tn.elitetunisia.common.audit.AuditService.class);
        svc = new TransferService(repo, bookings, assignments, drivers, audit);
    }

    private BookingEntity booking(UUID bookingId, Instant start, Instant end) {
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(b));
        when(b.getId()).thenReturn(bookingId);
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.CONFIRMED);
        when(b.getStartDateTime()).thenReturn(start); when(b.getEndDateTime()).thenReturn(end);
        tn.elitetunisia.vehicle.VehicleEntity v = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(v.getId()).thenReturn(UUID.randomUUID()); when(v.getPassengers()).thenReturn(5); when(b.getVehicle()).thenReturn(v);
        return b;
    }

    private static TransferRequest request(UUID bookingId, Instant pickupAt, UUID driverId) {
        return new TransferRequest(bookingId, pickupAt, "Aeroport", "Hotel", 2, driverId);
    }

    @Test
    void createReusesAssignment() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600); Instant end = start.plusSeconds(3600);
        BookingEntity b = booking(bookingId, start, end);
        when(drivers.findLockedById(driverId)).thenReturn(Optional.of(new tn.elitetunisia.driver.DriverEntity("A", "B", "+216")));
        when(assignments.findByBookingId(bookingId)).thenReturn(new BookingDriverAssignmentEntity(bookingId, driverId, start, end));
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var saved = svc.create(request(bookingId, start.plusSeconds(600), null), UUID.randomUUID(), null);
        assertEquals(driverId, saved.getDriverId());
        assertEquals(b.getVehicle().getId(), saved.getVehicleId());
    }

    @Test
    void createFailsWhenOutsidePeriod() {
        UUID bookingId = UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600); Instant end = start.plusSeconds(3600);
        booking(bookingId, start, end);
        var ex = assertThrows(tn.elitetunisia.common.exception.DomainException.class,
                () -> svc.create(request(bookingId, start.minusSeconds(10), null), UUID.randomUUID(), null));
        assertEquals("TRANSFER_OUTSIDE_BOOKING_PERIOD", ex.getCode());
    }

    @Test
    void createFailsIfDriverBusy() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600); Instant end = start.plusSeconds(3600);
        booking(bookingId, start, end);
        when(assignments.findByBookingId(bookingId)).thenReturn(null);
        when(drivers.findLockedById(driverId)).thenReturn(Optional.of(new tn.elitetunisia.driver.DriverEntity("A", "B", "+216")));
        when(assignments.overlaps(eq(driverId), any(), any(), eq(bookingId))).thenReturn(1L);
        var ex = assertThrows(tn.elitetunisia.common.exception.DomainException.class,
                () -> svc.create(request(bookingId, start.plusSeconds(10), driverId), UUID.randomUUID(), null));
        assertEquals("DRIVER_NOT_AVAILABLE", ex.getCode());
    }
}
