package tn.elitetunisia.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.common.storage.FileStorageService;

public class VehicleLifecycleProtectionTest {

    @Mock VehicleRepository vehicles;
    @Mock BrandRepository brands;
    @Mock CategoryRepository categories;
    @Mock BookingRepository bookings;
    @Mock AuditService audit;
    @Mock FileStorageService storage;
    @Mock tn.elitetunisia.partner.VehiclePartnerAssignmentRepository partnerAssignments;

    VehicleService svc;
    UUID vehicleId;
    VehicleEntity vehicle;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        svc = new VehicleService(vehicles, brands, categories, bookings, audit, storage, partnerAssignments);
        vehicleId = UUID.randomUUID();
        vehicle = mock(VehicleEntity.class);
        when(vehicle.getStatus()).thenReturn(VehicleStatus.AVAILABLE);
        when(vehicle.getBrand()).thenReturn(mock(tn.elitetunisia.brand.BrandEntity.class));
        when(vehicle.getCategory()).thenReturn(mock(tn.elitetunisia.category.CategoryEntity.class));
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
    }

    private void bookingsWith(BookingStatus status, long count) {
        when(bookings.countByVehicle(vehicleId, List.of(status))).thenReturn(count);
    }

    private void confirmedAhead(long count) {
        when(bookings.countByVehicleEndingAfter(eq(vehicleId), eq(BookingStatus.CONFIRMED), any(Instant.class))).thenReturn(count);
    }

    private void noActiveBooking() {
        bookingsWith(BookingStatus.IN_PROGRESS, 0);
        confirmedAhead(0);
    }

    private void setStatus(VehicleStatus status) {
        svc.updateStatus(vehicleId, new VehicleStatusRequest(status), UUID.randomUUID(), "127.0.0.1");
    }

    @Test
    void removalWithoutAnyBookingIsAllowed() {
        noActiveBooking();
        setStatus(VehicleStatus.UNAVAILABLE);
        verify(vehicle).setStatus(VehicleStatus.UNAVAILABLE);
    }

    @Test
    void removalWithConfirmedBookingAheadIsRefused() {
        bookingsWith(BookingStatus.IN_PROGRESS, 0);
        confirmedAhead(1);
        DomainException ex = assertThrows(DomainException.class, () -> setStatus(VehicleStatus.UNAVAILABLE));
        assertEquals("VEHICLE_HAS_ACTIVE_BOOKING", ex.getCode());
        verify(vehicle, never()).setStatus(any());
    }

    @Test
    void removalWithInProgressBookingIsRefused() {
        bookingsWith(BookingStatus.IN_PROGRESS, 1);
        DomainException ex = assertThrows(DomainException.class, () -> setStatus(VehicleStatus.UNAVAILABLE));
        assertEquals("VEHICLE_HAS_ACTIVE_BOOKING", ex.getCode());
        verify(vehicle, never()).setStatus(any());
    }

    @Test
    void maintenanceWithInProgressBookingIsRefused() {
        bookingsWith(BookingStatus.IN_PROGRESS, 1);
        DomainException ex = assertThrows(DomainException.class, () -> setStatus(VehicleStatus.MAINTENANCE));
        assertEquals("VEHICLE_HAS_ACTIVE_BOOKING", ex.getCode());
        verify(vehicle, never()).setStatus(any());
    }

    @Test
    void maintenanceWithConfirmedBookingAheadIsRefused() {
        bookingsWith(BookingStatus.IN_PROGRESS, 0);
        confirmedAhead(1);
        DomainException ex = assertThrows(DomainException.class, () -> setStatus(VehicleStatus.MAINTENANCE));
        assertEquals("VEHICLE_HAS_ACTIVE_BOOKING", ex.getCode());
        verify(vehicle, never()).setStatus(any());
    }

    @Test
    void statusChangeToAvailableIsAllowedEvenWithActiveBooking() {
        when(vehicle.getStatus()).thenReturn(VehicleStatus.MAINTENANCE);
        bookingsWith(BookingStatus.IN_PROGRESS, 1);
        confirmedAhead(1);
        setStatus(VehicleStatus.AVAILABLE);
        verify(vehicle).setStatus(VehicleStatus.AVAILABLE);
    }

    @Test
    void unchangedStatusIsNotBlocked() {
        when(vehicle.getStatus()).thenReturn(VehicleStatus.UNAVAILABLE);
        bookingsWith(BookingStatus.IN_PROGRESS, 1);
        setStatus(VehicleStatus.UNAVAILABLE);
        verify(vehicle).setStatus(VehicleStatus.UNAVAILABLE);
    }

    @Test
    void archivingWithInProgressBookingIsRefused() {
        bookingsWith(BookingStatus.IN_PROGRESS, 1);
        DomainException ex = assertThrows(DomainException.class, () -> setStatus(VehicleStatus.ARCHIVED));
        assertEquals("VEHICLE_HAS_ACTIVE_BOOKING", ex.getCode());
        verify(vehicle, never()).setStatus(any());
    }

    @Test
    void archivingWithConfirmedBookingAheadIsRefused() {
        bookingsWith(BookingStatus.IN_PROGRESS, 0);
        confirmedAhead(1);
        DomainException ex = assertThrows(DomainException.class, () -> setStatus(VehicleStatus.ARCHIVED));
        assertEquals("VEHICLE_HAS_ACTIVE_BOOKING", ex.getCode());
        verify(vehicle, never()).setStatus(any());
    }

    @Test
    void archivingWithoutActiveBookingIsAllowedAndKeepsHistory() {
        noActiveBooking();
        setStatus(VehicleStatus.ARCHIVED);
        verify(vehicle).setStatus(VehicleStatus.ARCHIVED);
        verify(vehicles, never()).delete(any(VehicleEntity.class));
    }

    @Test
    void restoringArchivedVehicleIsAllowed() {
        when(vehicle.getStatus()).thenReturn(VehicleStatus.ARCHIVED);
        setStatus(VehicleStatus.UNAVAILABLE);
        verify(vehicle).setStatus(VehicleStatus.UNAVAILABLE);
    }

    @Test
    void deleteWithoutBookingIsAllowed() {
        when(bookings.countByVehicle(vehicleId, List.of(BookingStatus.values()))).thenReturn(0L);
        svc.delete(vehicleId, UUID.randomUUID(), "127.0.0.1");
        verify(vehicles).delete(vehicle);
    }

    @Test
    void deleteWithAnyBookingIsRefusedToPreserveHistory() {
        when(bookings.countByVehicle(vehicleId, List.of(BookingStatus.values()))).thenReturn(3L);
        DomainException ex = assertThrows(DomainException.class,
                () -> svc.delete(vehicleId, UUID.randomUUID(), "127.0.0.1"));
        assertEquals("VEHICLE_HAS_BOOKINGS", ex.getCode());
        verify(vehicles, never()).delete(any(VehicleEntity.class));
    }

    @Test
    void unknownVehicleIsReportedAsNotFound() {
        UUID missing = UUID.randomUUID();
        when(vehicles.findLocked(missing)).thenReturn(Optional.empty());
        assertThrows(tn.elitetunisia.catalogue.CatalogueNotFoundException.class,
                () -> svc.updateStatus(missing, new VehicleStatusRequest(VehicleStatus.UNAVAILABLE), UUID.randomUUID(), "127.0.0.1"));
        verify(bookings, never()).countByVehicle(eq(missing), anyList());
    }
}
