package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.common.audit.AuditService;

public class BookingOperationServiceTest {
    BookingOperationRepository ops;
    BookingRepository bookings;
    VehicleRepository vehicles;
    AuditService audit;
    BookingEventRepository events;
    tn.elitetunisia.invoice.InvoiceService invoices;
    BookingOperationService svc;

    @BeforeEach
    void init() {
        ops = mock(BookingOperationRepository.class);
        bookings = mock(BookingRepository.class);
        vehicles = mock(VehicleRepository.class);
        audit = mock(AuditService.class);
        events = mock(BookingEventRepository.class);
        invoices = mock(tn.elitetunisia.invoice.InvoiceService.class);
        svc = new BookingOperationService(ops, bookings, vehicles, audit, events, invoices);
    }

    @Test
    void pickupHappyPath() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        var veh = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(booking.getVehicle()).thenReturn(veh);
        when(vehicles.findLocked(any())).thenReturn(Optional.of(veh));
        var veh2 = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(booking.getVehicle()).thenReturn(veh2);
        when(vehicles.findLocked(any())).thenReturn(Optional.of(veh2));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of());
        when(ops.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var op = svc.recordPickup(bookingId, actor, 1000, 80);
        assertNotNull(op);
        verify(ops).saveAndFlush(any());
        verify(bookings).flush();
    }

    @Test
    void pickupInvalidOdometer() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        var veh3 = mock(tn.elitetunisia.vehicle.VehicleEntity.class);
        when(booking.getVehicle()).thenReturn(veh3);
        when(vehicles.findLocked(any())).thenReturn(Optional.of(veh3));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of());

        var ex = assertThrows(BookingException.class, () -> svc.recordPickup(bookingId, actor, -5, 10));
        assertEquals("INVALID_DATA", ex.getCode());
    }

    @Test
    void returnWithoutPickupFails() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of());

        var ex = assertThrows(BookingException.class, () -> svc.recordReturn(bookingId, actor, 1200, 60, null, BigDecimal.ZERO));
        assertEquals("INVALID_OPERATION", ex.getCode());
    }

    @Test
    void pickupAndReturnValidateFuelAndCharges() {
        UUID bookingId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getVehicle()).thenReturn(mock(VehicleEntity.class));
        when(vehicles.findLocked(any())).thenReturn(Optional.of(mock(VehicleEntity.class)));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of());

        assertEquals("INVALID_DATA", assertThrows(BookingException.class,
                () -> svc.recordPickup(bookingId, actor, 100, 101)).getCode());

        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(
                new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO)));
        assertEquals("INVALID_DATA", assertThrows(BookingException.class,
                () -> svc.recordReturn(bookingId, actor, 120, 50, null, new BigDecimal("-1"))).getCode());
        assertEquals("INVALID_DATA", assertThrows(BookingException.class,
                () -> svc.recordReturn(bookingId, actor, 120, 101, null, BigDecimal.ZERO)).getCode());
    }
}
