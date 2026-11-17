package tn.elitetunisia.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.brand.BrandEntity;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryEntity;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.common.config.TestProfileAdminSeeder;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.partner.PartnerDtos.AssignVehicleRequest;
import tn.elitetunisia.vehicle.FuelType;
import tn.elitetunisia.vehicle.TransmissionType;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleStatus;

/** Historique des affectations véhicule-partenaire, sur le contexte Spring réel (H2, transactions de test annulées). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PartnerAssignmentHistoryTest {
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired private PartnerService partnerService;
    @Autowired private PartnerRepository partners;
    @Autowired private VehiclePartnerAssignmentRepository assignments;
    @Autowired private VehicleRepository vehicles;
    @Autowired private BrandRepository brands;
    @Autowired private CategoryRepository categories;
    @Autowired private UserRepository users;

    private UUID actor;
    private UUID vehicleId;
    private UUID partnerA;
    private UUID partnerB;
    private UUID partnerInactive;

    @BeforeEach
    void setUp() {
        actor = users.findByEmailIgnoreCase(TestProfileAdminSeeder.EMAIL).orElseThrow().getId();
        BrandEntity brand = brands.saveAndFlush(new BrandEntity("Marque H " + RUN_ID, "marque-h-" + RUN_ID, "Test", null, true));
        CategoryEntity category = categories.saveAndFlush(new CategoryEntity("Cat H " + RUN_ID, "cat-h-" + RUN_ID, "Test", true));
        VehicleEntity vehicle = new VehicleEntity(brand, category, "Modele H " + RUN_ID, 2024, "Test",
                BigDecimal.valueOf(200), BigDecimal.valueOf(1300), null, "TND", 5, 4,
                TransmissionType.AUTOMATIC, FuelType.PETROL, false, VehicleStatus.AVAILABLE);
        vehicleId = vehicles.saveAndFlush(vehicle).getId();
        partnerA = newPartner("A");
        partnerB = newPartner("B");
        partnerInactive = newPartner("C");
        partnerService.deactivate(partnerInactive, actor, "ip");
    }

    @Test
    void firstAssignmentCreatesOpenRowAndSetsCurrentPartner() {
        partnerService.assign(vehicleId, new AssignVehicleRequest(partnerA, "Première affectation"), actor, "ip");

        List<VehiclePartnerAssignmentEntity> rows = assignments.findByVehicleIdOrderByAssignedAtDesc(vehicleId);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getEndedAt());
        assertEquals(partnerA, vehicles.findById(vehicleId).orElseThrow().getPartner().getId());
    }

    @Test
    void transferKeepsOldRowsAndLeavesSingleActiveAssignment() {
        partnerService.assign(vehicleId, new AssignVehicleRequest(partnerA, "Initial"), actor, "ip");
        partnerService.assign(vehicleId, new AssignVehicleRequest(partnerB, "Transfert"), actor, "ip");

        List<VehiclePartnerAssignmentEntity> rows = assignments.findByVehicleIdOrderByAssignedAtDesc(vehicleId);
        assertEquals(2, rows.size(), "l'ancienne affectation doit être conservée");
        long active = rows.stream().filter(r -> r.getEndedAt() == null).count();
        assertEquals(1, active, "un seul rattachement actif par véhicule");
        VehiclePartnerAssignmentEntity closed = rows.stream().filter(r -> r.getPartner().getId().equals(partnerA)).findFirst().orElseThrow();
        assertNotNull(closed.getEndedAt());
        assertEquals(partnerB, vehicles.findById(vehicleId).orElseThrow().getPartner().getId());
    }

    @Test
    void rejectedAssignmentToInactivePartnerLeavesHistoryUnchanged() {
        partnerService.assign(vehicleId, new AssignVehicleRequest(partnerA, "Initial"), actor, "ip");

        DomainException ex = assertThrows(DomainException.class,
                () -> partnerService.assign(vehicleId, new AssignVehicleRequest(partnerInactive, "Refusé"), actor, "ip"));
        assertEquals("PARTNER_INACTIVE", ex.getCode());

        List<VehiclePartnerAssignmentEntity> rows = assignments.findByVehicleIdOrderByAssignedAtDesc(vehicleId);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getEndedAt());
        assertEquals(partnerA, vehicles.findById(vehicleId).orElseThrow().getPartner().getId());
        assertTrue(assignments.findFirstByVehicleIdAndEndedAtIsNull(vehicleId).isPresent());
    }

    private UUID newPartner(String label) {
        PartnerEntity partner = new PartnerEntity("Partenaire " + label + " " + RUN_ID, PartnerType.COMPANY);
        partner.update("Partenaire " + label + " " + RUN_ID, PartnerType.COMPANY, null, null, null, null,
                "TAX-" + UUID.randomUUID(), null);
        return partners.saveAndFlush(partner).getId();
    }
}
