package tn.elitetunisia.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.partner.PartnerDtos.AssignVehicleRequest;
import tn.elitetunisia.partner.PartnerDtos.PartnerRequest;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;

class PartnerServiceTest {

    private PartnerRepository partners;
    private VehiclePartnerAssignmentRepository assignments;
    private VehicleRepository vehicles;
    private UserRepository users;
    private BookingRepository bookings;
    private GuaranteeRepository guarantees;
    private AuditService audit;
    private PartnerService service;
    private final UUID actorId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        partners = mock(PartnerRepository.class);
        assignments = mock(VehiclePartnerAssignmentRepository.class);
        vehicles = mock(VehicleRepository.class);
        users = mock(UserRepository.class);
        bookings = mock(BookingRepository.class);
        guarantees = mock(GuaranteeRepository.class);
        audit = mock(AuditService.class);
        service = new PartnerService(partners, assignments, vehicles, users, bookings, guarantees, audit);
        when(partners.save(any(PartnerEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById(actorId)).thenReturn(Optional.of(mock(UserEntity.class)));
    }

    private static PartnerRequest request(PartnerType type, String name, String taxId, UUID userId) {
        return new PartnerRequest(type, name, null, null, null, null, taxId, userId);
    }

    @Test
    void createsIndividualPartnerWithoutTaxIdentifier() {
        var created = service.create(request(PartnerType.INDIVIDUAL, "  Ali Ben  ", null, null), actorId, "ip");
        assertEquals(PartnerType.INDIVIDUAL, created.partnerType());
        assertEquals("Ali Ben", created.name());
        assertTrue(created.active());
        verify(audit).recordAudit(actorId, "PARTNER_CREATED", "PARTNER", created.id(), "ip", "SUCCESS");
    }

    @Test
    void createsCompanyPartnerWithTaxIdentifier() {
        var created = service.create(request(PartnerType.COMPANY, "Auto SARL", "1234567A", null), actorId, "ip");
        assertEquals(PartnerType.COMPANY, created.partnerType());
        assertEquals("1234567A", created.taxIdentifier());
    }

    @Test
    void companyRequiresTaxIdentifier() {
        var ex = assertThrows(DomainException.class,
                () -> service.create(request(PartnerType.COMPANY, "Auto SARL", " ", null), actorId, "ip"));
        assertEquals("PARTNER_TAX_IDENTIFIER_REQUIRED", ex.getCode());
    }

    @Test
    void individualRejectsTaxIdentifier() {
        var ex = assertThrows(DomainException.class,
                () -> service.create(request(PartnerType.INDIVIDUAL, "Ali", "123", null), actorId, "ip"));
        assertEquals("PARTNER_TAX_IDENTIFIER_NOT_ALLOWED", ex.getCode());
    }

    @Test
    void blankNameIsRejected() {
        var ex = assertThrows(DomainException.class,
                () -> service.create(request(PartnerType.INDIVIDUAL, "   ", null, null), actorId, "ip"));
        assertEquals("PARTNER_NAME_REQUIRED", ex.getCode());
    }

    @Test
    void partnerLinkedUserMustHavePartnerRole() {
        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(user.getRoleKeys()).thenReturn(Set.of("CUSTOMER"));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        var ex = assertThrows(DomainException.class,
                () -> service.create(request(PartnerType.INDIVIDUAL, "Ali", null, userId), actorId, "ip"));
        assertEquals("USER_NOT_PARTNER_ROLE", ex.getCode());
    }

    @Test
    void deactivationIsIdempotentAndActivationRestoresPartner() {
        PartnerEntity partner = new PartnerEntity("Ali", PartnerType.INDIVIDUAL);
        when(partners.findById(partner.getId())).thenReturn(Optional.of(partner));
        service.deactivate(partner.getId(), actorId, "ip");
        service.deactivate(partner.getId(), actorId, "ip");
        assertFalse(partner.isActive());
        verify(audit, times(1)).recordAudit(actorId, "PARTNER_DEACTIVATED", "PARTNER", partner.getId(), "ip", "SUCCESS");
        service.activate(partner.getId(), actorId, "ip");
        assertTrue(partner.isActive());
    }

    @Test
    void firstAssignmentCreatesHistoryWithoutClosingAnything() {
        PartnerEntity partner = new PartnerEntity("Auto SARL", PartnerType.COMPANY);
        VehicleEntity vehicle = mock(VehicleEntity.class);
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        when(partners.findById(partner.getId())).thenReturn(Optional.of(partner));
        when(assignments.findFirstByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.empty());
        when(assignments.save(any(VehiclePartnerAssignmentEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.assign(vehicleId, new AssignVehicleRequest(partner.getId(), "Mise en location"), actorId, "ip");

        verify(vehicle).setPartner(partner);
        verify(assignments).save(any(VehiclePartnerAssignmentEntity.class));
        verify(assignments, never()).saveAndFlush(any());
        verify(audit).recordAudit(actorId, "VEHICLE_PARTNER_ASSIGNED", "VEHICLE", vehicleId, "ip", "SUCCESS");
    }

    @Test
    void transferClosesPreviousAssignmentAndKeepsIt() {
        PartnerEntity oldPartner = new PartnerEntity("Ancien", PartnerType.INDIVIDUAL);
        PartnerEntity newPartner = new PartnerEntity("Nouveau", PartnerType.COMPANY);
        VehicleEntity vehicle = mock(VehicleEntity.class);
        VehicleEntity vehicleOfOld = mock(VehicleEntity.class);
        VehiclePartnerAssignmentEntity current = new VehiclePartnerAssignmentEntity(vehicleOfOld, oldPartner,
                "Initial", mock(UserEntity.class), java.time.Instant.now().minusSeconds(3600));
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        when(partners.findById(newPartner.getId())).thenReturn(Optional.of(newPartner));
        when(assignments.findFirstByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.of(current));
        when(assignments.save(any(VehiclePartnerAssignmentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(bookings.countByVehicle(eq(vehicleId), anyList())).thenReturn(0L);

        service.assign(vehicleId, new AssignVehicleRequest(newPartner.getId(), "Transfert"), actorId, "ip");

        assertFalse(current.getEndedAt() == null, "l'ancienne affectation doit être fermée");
        assertEquals(oldPartner.getId(), current.getPartner().getId(), "l'ancienne affectation reste identique");
        verify(assignments).saveAndFlush(current);
        verify(vehicle).setPartner(newPartner);
        verify(audit).recordAudit(actorId, "VEHICLE_PARTNER_TRANSFERRED", "VEHICLE", vehicleId, "ip", "SUCCESS");
    }

    @Test
    void transferIsRefusedWhenBlockingBookingExists() {
        PartnerEntity oldPartner = new PartnerEntity("Ancien", PartnerType.INDIVIDUAL);
        PartnerEntity newPartner = new PartnerEntity("Nouveau", PartnerType.COMPANY);
        VehicleEntity vehicle = mock(VehicleEntity.class);
        VehiclePartnerAssignmentEntity current = new VehiclePartnerAssignmentEntity(vehicle, oldPartner,
                "Initial", mock(UserEntity.class), java.time.Instant.now().minusSeconds(60));
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(vehicle));
        when(partners.findById(newPartner.getId())).thenReturn(Optional.of(newPartner));
        when(assignments.findFirstByVehicleIdAndEndedAtIsNull(vehicleId)).thenReturn(Optional.of(current));
        when(bookings.countByVehicle(eq(vehicleId), anyList())).thenReturn(1L);

        var ex = assertThrows(DomainException.class, () -> service.assign(vehicleId,
                new AssignVehicleRequest(newPartner.getId(), "Transfert"), actorId, "ip"));
        assertEquals("VEHICLE_TRANSFER_BLOCKED_BY_BOOKING", ex.getCode());
        assertNull(current.getEndedAt());
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test
    void inactivePartnerCannotReceiveVehicle() {
        PartnerEntity partner = new PartnerEntity("Inactif", PartnerType.INDIVIDUAL);
        partner.deactivate(java.time.Instant.now());
        when(vehicles.findLocked(vehicleId)).thenReturn(Optional.of(mock(VehicleEntity.class)));
        when(partners.findById(partner.getId())).thenReturn(Optional.of(partner));
        var ex = assertThrows(DomainException.class, () -> service.assign(vehicleId,
                new AssignVehicleRequest(partner.getId(), "Test"), actorId, "ip"));
        assertEquals("PARTNER_INACTIVE", ex.getCode());
    }

    @Test
    void partnerSeesOnlyOwnVehicles() {
        UUID userId = UUID.randomUUID();
        PartnerEntity mine = new PartnerEntity("Mine", PartnerType.INDIVIDUAL);
        PartnerEntity other = new PartnerEntity("Other", PartnerType.INDIVIDUAL);
        VehicleEntity foreign = mock(VehicleEntity.class);
        when(foreign.getPartner()).thenReturn(other);
        when(partners.findByUserId(userId)).thenReturn(Optional.of(mine));
        when(vehicles.findById(vehicleId)).thenReturn(Optional.of(foreign));

        var ex = assertThrows(DomainException.class, () -> service.ownVehicle(userId, vehicleId));
        assertEquals("VEHICLE_NOT_FOUND", ex.getCode());
    }

    @Test
    void userWithoutPartnerProfileGetsNoData() {
        UUID userId = UUID.randomUUID();
        when(partners.findByUserId(userId)).thenReturn(Optional.empty());
        var ex = assertThrows(DomainException.class, () -> service.ownVehicles(userId));
        assertEquals("PARTNER_PROFILE_NOT_FOUND", ex.getCode());
    }

    private static void assertNull(Object value) {
        assertEquals(null, value);
    }
}
