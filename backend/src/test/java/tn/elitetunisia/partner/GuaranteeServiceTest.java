package tn.elitetunisia.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeRequest;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleStatus;

class GuaranteeServiceTest {

    private GuaranteeRepository guarantees;
    private PartnerRepository partners;
    private VehicleRepository vehicles;
    private UserRepository users;
    private BookingRepository bookings;
    private AuditService audit;
    private GuaranteeService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final UUID partnerId = UUID.randomUUID();

    private VehicleEntity vehicle;
    private PartnerEntity partner;
    private UserEntity actor;

    @BeforeEach
    void setUp() {
        guarantees = mock(GuaranteeRepository.class);
        partners = mock(PartnerRepository.class);
        vehicles = mock(VehicleRepository.class);
        users = mock(UserRepository.class);
        bookings = mock(BookingRepository.class);
        audit = mock(AuditService.class);
        service = new GuaranteeService(guarantees, partners, vehicles, users, bookings, audit);

        vehicle = mock(VehicleEntity.class);
        partner = mock(PartnerEntity.class);
        actor = mock(UserEntity.class);
        when(vehicle.getId()).thenReturn(vehicleId);
        when(vehicle.getPartner()).thenReturn(partner);
        when(vehicle.getStatus()).thenReturn(VehicleStatus.AVAILABLE);
        when(partner.getId()).thenReturn(partnerId);
        when(partner.getName()).thenReturn("Auto SARL");
        when(partner.isActive()).thenReturn(true);
        when(actor.getId()).thenReturn(actorId);

        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        when(users.findById(actorId)).thenReturn(Optional.of(actor));
        when(guarantees.saveAndFlush(any(GuaranteeEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private GuaranteeRequest request(Instant start, Instant end) {
        return new GuaranteeRequest(vehicleId, start, end, "  Garantie hiver  ");
    }

    private GuaranteeRequest validRequest() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return request(start, start.plus(2, ChronoUnit.DAYS));
    }

    private GuaranteeEntity existingGuarantee(Instant start, Instant end) {
        return new GuaranteeEntity(vehicle, partner, start, end, "reason", actor, Instant.now());
    }

    @Test
    void createsGuaranteeForCurrentPartnerAndRecordsAudit() {
        var created = service.create(validRequest(), actorId, "10.0.0.1");

        assertEquals(vehicleId, created.vehicleId());
        assertEquals(partnerId, created.partnerId());
        assertEquals("Garantie hiver", created.reason());
        assertEquals(GuaranteeStatus.ACTIVE, created.status());
        verify(audit).recordAudit(actorId, "GUARANTEE_CREATED", "VEHICLE", vehicleId, "10.0.0.1", "SUCCESS");
    }

    @Test
    void rejectsVehicleWithoutPartner() {
        when(vehicle.getPartner()).thenReturn(null);
        assertCode("VEHICLE_HAS_NO_PARTNER", () -> service.create(validRequest(), actorId, "ip"));
        verify(guarantees, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInactivePartner() {
        when(partner.isActive()).thenReturn(false);
        assertCode("PARTNER_INACTIVE", () -> service.create(validRequest(), actorId, "ip"));
        verify(guarantees, never()).saveAndFlush(any());
    }

    @Test
    void rejectsArchivedVehicle() {
        when(vehicle.getStatus()).thenReturn(VehicleStatus.ARCHIVED);
        assertCode("VEHICLE_ARCHIVED", () -> service.create(validRequest(), actorId, "ip"));
    }

    @Test
    void rejectsUnknownVehicle() {
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.empty());
        assertCode("VEHICLE_NOT_FOUND", () -> service.create(validRequest(), actorId, "ip"));
    }

    @Test
    void rejectsEndNotAfterStart() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        assertCode("GUARANTEE_PERIOD_INVALID", () -> service.create(request(start, start), actorId, "ip"));
        assertCode("GUARANTEE_PERIOD_INVALID",
                () -> service.create(request(start, start.minus(1, ChronoUnit.HOURS)), actorId, "ip"));
    }

    @Test
    void rejectsStartInThePast() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        assertCode("GUARANTEE_START_IN_PAST",
                () -> service.create(request(start, start.plus(2, ChronoUnit.DAYS)), actorId, "ip"));
    }

    @Test
    void rejectsDurationAboveNinetyDaysAndAcceptsExactlyNinety() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        assertCode("GUARANTEE_TOO_LONG",
                () -> service.create(request(start, start.plus(91, ChronoUnit.DAYS)), actorId, "ip"));
        var created = service.create(request(start, start.plus(90, ChronoUnit.DAYS)), actorId, "ip");
        assertEquals(GuaranteeStatus.ACTIVE, created.status());
    }

    @Test
    void rejectsOverlapDetectedBeforeInsert() {
        when(guarantees.countOverlapping(eq(vehicleId), eq(GuaranteeStatus.ACTIVE), any(), any())).thenReturn(1L);
        assertCode("GUARANTEE_OVERLAP", () -> service.create(validRequest(), actorId, "ip"));
        verify(guarantees, never()).saveAndFlush(any());
    }

    @Test
    void mapsExclusionViolationRaisedByDatabaseToOverlap() {
        when(guarantees.saveAndFlush(any(GuaranteeEntity.class))).thenThrow(new DataIntegrityViolationException(
                "conflit", new SQLException("exclusion", "23P01")));
        assertCode("GUARANTEE_OVERLAP", () -> service.create(validRequest(), actorId, "ip"));
    }

    @Test
    void rethrowsOtherIntegrityViolations() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "autre", new SQLException("check", "23514"));
        when(guarantees.saveAndFlush(any(GuaranteeEntity.class))).thenThrow(failure);
        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> service.create(validRequest(), actorId, "ip"));
        assertSame(failure, ex);
    }

    @Test
    void withdrawsActiveGuaranteeWithoutBlockingBookings() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        GuaranteeEntity guarantee = existingGuarantee(start, start.plus(2, ChronoUnit.DAYS));
        UUID guaranteeId = guarantee.getId();
        when(guarantees.findVehicleIdById(guaranteeId)).thenReturn(Optional.of(vehicleId));
        when(guarantees.findById(guaranteeId)).thenReturn(Optional.of(guarantee));
        when(bookings.overlaps(eq(vehicleId), any(), any(), anyList(), any())).thenReturn(0L);

        var withdrawn = service.withdraw(guaranteeId, actorId, "ip");

        assertEquals(GuaranteeStatus.WITHDRAWN, withdrawn.status());
        assertEquals(actorId, withdrawn.withdrawnByUserId());
        verify(audit).recordAudit(actorId, "GUARANTEE_WITHDRAWN", "VEHICLE", vehicleId, "ip", "SUCCESS");
    }

    @Test
    void refusesWithdrawalWhenConfirmedOrInProgressBookingOverlaps() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        GuaranteeEntity guarantee = existingGuarantee(start, start.plus(2, ChronoUnit.DAYS));
        UUID guaranteeId = guarantee.getId();
        when(guarantees.findVehicleIdById(guaranteeId)).thenReturn(Optional.of(vehicleId));
        when(guarantees.findById(guaranteeId)).thenReturn(Optional.of(guarantee));
        when(bookings.overlaps(eq(vehicleId), any(), any(), eq(GuaranteeService.BLOCKING_WITHDRAWAL_STATUSES),
                any())).thenReturn(1L);
        assertCode("GUARANTEE_HAS_BOOKINGS", () -> service.withdraw(guaranteeId, actorId, "ip"));
        assertEquals(GuaranteeStatus.ACTIVE, guarantee.getStatus());
    }

    @Test
    void refusesWithdrawalOfAlreadyWithdrawnGuarantee() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        GuaranteeEntity guarantee = existingGuarantee(start, start.plus(2, ChronoUnit.DAYS));
        guarantee.withdraw(actor, Instant.now());
        UUID guaranteeId = guarantee.getId();
        when(guarantees.findVehicleIdById(guaranteeId)).thenReturn(Optional.of(vehicleId));
        when(guarantees.findById(guaranteeId)).thenReturn(Optional.of(guarantee));
        assertCode("GUARANTEE_NOT_ACTIVE", () -> service.withdraw(guaranteeId, actorId, "ip"));
    }

    @Test
    void refusesWithdrawalOfExpiredGuarantee() {
        Instant start = Instant.now().minus(3, ChronoUnit.DAYS);
        GuaranteeEntity guarantee = existingGuarantee(start, Instant.now().minus(1, ChronoUnit.DAYS));
        UUID guaranteeId = guarantee.getId();
        when(guarantees.findVehicleIdById(guaranteeId)).thenReturn(Optional.of(vehicleId));
        when(guarantees.findById(guaranteeId)).thenReturn(Optional.of(guarantee));
        assertCode("GUARANTEE_EXPIRED", () -> service.withdraw(guaranteeId, actorId, "ip"));
    }

    @Test
    void reportsUnknownGuaranteeOnWithdrawal() {
        UUID missing = UUID.randomUUID();
        when(guarantees.findVehicleIdById(missing)).thenReturn(Optional.empty());
        assertCode("GUARANTEE_NOT_FOUND", () -> service.withdraw(missing, actorId, "ip"));
    }

    @Test
    void checksBlockingStatusesAreConfirmedAndInProgress() {
        assertEquals(java.util.List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
                GuaranteeService.BLOCKING_WITHDRAWAL_STATUSES);
    }

    private static void assertCode(String expected, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(DomainException.class, action);
        assertEquals(expected, ex.getCode());
    }
}
