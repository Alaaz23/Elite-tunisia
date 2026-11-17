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
    BookingService bookingService;
    tn.elitetunisia.billing.BillingRequestService billing;
    BookingOperationService svc;

    @BeforeEach
    void init() {
        ops = mock(BookingOperationRepository.class);
        bookings = mock(BookingRepository.class);
        vehicles = mock(VehicleRepository.class);
        audit = mock(AuditService.class);
        events = mock(BookingEventRepository.class);
        invoices = mock(tn.elitetunisia.invoice.InvoiceService.class);
        bookingService = mock(BookingService.class);
        when(bookingService.lockForOperation(any())).thenAnswer(i -> bookings.findLocked(i.getArgument(0)).orElse(null));
        billing = mock(tn.elitetunisia.billing.BillingRequestService.class);
        svc = new BookingOperationService(ops, bookings, vehicles, audit, events, invoices, bookingService, billing);
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
    void secondPickupOnInProgressBookingReturnsExistingOperationWithoutDuplicate() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.IN_PROGRESS);
        var existingPickup = new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO);
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(existingPickup));

        var op = svc.recordPickup(bookingId, actor, 100, 75);

        assertSame(existingPickup, op);
        assertEquals(BookingStatus.IN_PROGRESS, booking.getStatus());
        verify(ops, never()).saveAndFlush(any());
        verify(booking, never()).transition(any(), any());
        verify(events, never()).saveAndFlush(any());
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
    void returnRejectsBookingNotInProgressBeforeAnyWrite() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(
                new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO)));

        var ex = assertThrows(BookingException.class, () -> svc.recordReturn(bookingId, actor, 120, 50, null, BigDecimal.ZERO));
        assertEquals("INVALID_STATUS_TRANSITION", ex.getCode());
        verify(ops, never()).saveAndFlush(any());
        verify(bookingService, never()).completeAfterReturn(any(), any(), any());
    }

    @Test
    void validReturnDelegatesCompletionToBookingService() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.IN_PROGRESS);
        when(booking.getVehicle()).thenReturn(mock(VehicleEntity.class));
        when(vehicles.findLocked(any())).thenReturn(Optional.of(mock(VehicleEntity.class)));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(
                new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO)));
        when(ops.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        svc.recordReturn(bookingId, actor, 120, 50, null, BigDecimal.ZERO);

        verify(bookingService).completeAfterReturn(booking, actor, "BOOKING_RETURN");
    }

    @Test
    void mainInvoiceRequestIsEnqueuedOnlyAfterCompletionInSameTransaction() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.IN_PROGRESS);
        when(booking.getVehicle()).thenReturn(mock(VehicleEntity.class));
        when(vehicles.findLocked(any())).thenReturn(Optional.of(mock(VehicleEntity.class)));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(
                new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO)));
        when(ops.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        svc.recordReturn(bookingId, actor, 120, 50, null, BigDecimal.ZERO);

        var order = inOrder(bookingService, billing);
        order.verify(bookingService).completeAfterReturn(booking, actor, "BOOKING_RETURN");
        order.verify(billing).enqueueMainInvoice(bookingId, actor);
        verifyNoInteractions(invoices);
    }

    @Test
    void failingToEnqueueMainInvoiceRollsBackReturnByPropagation() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.IN_PROGRESS);
        when(booking.getVehicle()).thenReturn(mock(VehicleEntity.class));
        when(vehicles.findLocked(any())).thenReturn(Optional.of(mock(VehicleEntity.class)));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(
                new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO)));
        when(ops.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new IllegalStateException("outbox failed")).when(billing).enqueueMainInvoice(any(), any());

        assertThrows(IllegalStateException.class, () -> svc.recordReturn(bookingId, actor, 120, 50, null, BigDecimal.ZERO));
    }

    @Test
    void completionFailureIsPropagatedNotSwallowed() {
        UUID bookingId = UUID.randomUUID(); UUID actor = UUID.randomUUID();
        BookingEntity booking = mock(BookingEntity.class);
        when(bookings.findLocked(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getStatus()).thenReturn(BookingStatus.IN_PROGRESS);
        when(booking.getVehicle()).thenReturn(mock(VehicleEntity.class));
        when(vehicles.findLocked(any())).thenReturn(Optional.of(mock(VehicleEntity.class)));
        when(ops.findByBookingIdOrderByCreatedAtAsc(bookingId)).thenReturn(List.of(
                new BookingOperationEntity(bookingId, actor, "PICKUP", 100, null, 75, null, null, BigDecimal.ZERO)));
        when(ops.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new BookingException("AUDIT_FAILED", "audit")).when(bookingService).completeAfterReturn(any(), any(), any());

        var ex = assertThrows(BookingException.class, () -> svc.recordReturn(bookingId, actor, 120, 50, null, BigDecimal.ZERO));
        assertEquals("AUDIT_FAILED", ex.getCode());
        verify(billing, never()).enqueueMainInvoice(any(), any());
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
