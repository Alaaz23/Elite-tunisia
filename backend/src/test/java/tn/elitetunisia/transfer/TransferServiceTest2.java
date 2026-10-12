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
    tn.elitetunisia.driver.DriverAssignmentService assignSvc;
    TransferService svc;

    @BeforeEach
    void init() {
        repo = mock(TransferRepository.class);
        bookings = mock(BookingRepository.class);
        assignments = mock(BookingDriverAssignmentRepository.class);
        assignSvc = mock(tn.elitetunisia.driver.DriverAssignmentService.class);
        svc = new TransferService(repo, bookings, assignments, assignSvc);
    }

    @Test
    void createForBookingReusesAssignment() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.CONFIRMED);
        Instant start = Instant.now().plusSeconds(3600); Instant end = start.plusSeconds(3600);
        when(b.getStartDateTime()).thenReturn(start); when(b.getEndDateTime()).thenReturn(end);
        tn.elitetunisia.vehicle.VehicleEntity v = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(v.getId()).thenReturn(UUID.randomUUID()); when(b.getVehicle()).thenReturn(v);

        BookingDriverAssignmentEntity a = new BookingDriverAssignmentEntity(bookingId, driverId, start, end);
        when(assignments.findByBookingId(bookingId)).thenReturn(a);

        TransferEntity t = new TransferEntity(); t.setPickupAt(start.plusSeconds(600)); t.setPassengers(2);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = svc.createForBooking(bookingId, t);
        assertEquals(driverId, saved.getDriverId());
        assertEquals(v.getId(), saved.getVehicleId());
    }

    @Test
    void createForBookingFailsWhenOutsidePeriod() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.CONFIRMED);
        Instant start = Instant.now().plusSeconds(3600); Instant end = start.plusSeconds(3600);
        when(b.getStartDateTime()).thenReturn(start); when(b.getEndDateTime()).thenReturn(end);
        tn.elitetunisia.vehicle.VehicleEntity v = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(v.getId()).thenReturn(UUID.randomUUID()); when(b.getVehicle()).thenReturn(v);

        TransferEntity t = new TransferEntity(); t.setPickupAt(start.minusSeconds(10));
        assertThrows(RuntimeException.class, () -> svc.createForBooking(bookingId, t));
    }

    @Test
    void createForBookingFailsIfDriverBusy() {
        UUID bookingId = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        BookingEntity b = mock(BookingEntity.class);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(b));
        when(b.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.CONFIRMED);
        Instant start = Instant.now().plusSeconds(3600); Instant end = start.plusSeconds(3600);
        when(b.getStartDateTime()).thenReturn(start); when(b.getEndDateTime()).thenReturn(end);
        tn.elitetunisia.vehicle.VehicleEntity v = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(v.getId()).thenReturn(UUID.randomUUID()); when(b.getVehicle()).thenReturn(v);

        when(assignments.findByBookingId(bookingId)).thenReturn(null);
        TransferEntity t = new TransferEntity(); t.setPickupAt(start.plusSeconds(10)); t.setDriverId(driverId);
        when(assignments.overlaps(eq(driverId), any(), any(), eq(bookingId))).thenReturn(1L);
        assertThrows(RuntimeException.class, () -> svc.createForBooking(bookingId, t));
    }
}
