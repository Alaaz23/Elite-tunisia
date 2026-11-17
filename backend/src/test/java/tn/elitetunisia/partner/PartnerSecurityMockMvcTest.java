package tn.elitetunisia.partner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import tn.elitetunisia.auth.entity.PermissionEntity;
import tn.elitetunisia.auth.entity.RoleEntity;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.PermissionRepository;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.brand.BrandEntity;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryEntity;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.common.config.TestProfileAdminSeeder;
import tn.elitetunisia.vehicle.FuelType;
import tn.elitetunisia.vehicle.TransmissionType;
import tn.elitetunisia.vehicle.VehicleEntity;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleStatus;

/**
 * Tests MockMvc de la chaîne HTTP réelle (filtres Spring Security, contrôleurs, @PreAuthorize)
 * pour le rôle PARTNER : autorisations, isolation entre partenaires et codes HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartnerSecurityMockMvcTest {
    private static final String PASSWORD = "Strong-Pass-123!";
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PartnerRepository partners;
    @Autowired private BrandRepository brands;
    @Autowired private CategoryRepository categories;
    @Autowired private VehicleRepository vehicles;
    @Autowired private GuaranteeRepository guarantees;

    private UUID vehicleA;
    private UUID vehicleB;
    private UUID partnerAId;
    private String partnerAToken;
    private String partnerBToken;

    @BeforeEach
    void setUp() throws Exception {
        PermissionEntity portalRead = permissions.findById("partner-portal:read")
                .orElseGet(() -> permissions.save(new PermissionEntity("partner-portal:read", "Consulter l'espace partenaire")));
        RoleEntity partnerRole = roles.findById("PARTNER")
                .orElseGet(() -> roles.save(new RoleEntity("PARTNER", "Partenaire")));
        partnerRole.addPermission(portalRead);
        roles.save(partnerRole);

        // Reproduit les grants SUPER_ADMIN de V23 : le seeder H2 ne connaît pas ces permissions.
        RoleEntity superAdmin = roles.findById("SUPER_ADMIN").orElseThrow();
        for (String key : new String[] {"partners:read", "partners:manage"}) {
            PermissionEntity permission = permissions.findById(key)
                    .orElseGet(() -> permissions.save(new PermissionEntity(key, "Gestion partenaires")));
            superAdmin.addPermission(permission);
        }
        roles.save(superAdmin);

        BrandEntity brand = brands.saveAndFlush(new BrandEntity("Marque " + RUN_ID, "marque-" + RUN_ID, "Test", null, true));
        CategoryEntity category = categories.saveAndFlush(new CategoryEntity("Cat " + RUN_ID, "cat-" + RUN_ID, "Test", true));

        UserEntity userA = createPartnerUser("partner-a-" + RUN_ID + "@example.test", partnerRole);
        UserEntity userB = createPartnerUser("partner-b-" + RUN_ID + "@example.test", partnerRole);
        PartnerEntity partnerA = linkPartner("Partenaire A " + RUN_ID, userA);
        PartnerEntity partnerB = linkPartner("Partenaire B " + RUN_ID, userB);
        partnerAId = partnerA.getId();

        vehicleA = saveVehicle(brand, category, "Modele A " + RUN_ID, partnerA);
        vehicleB = saveVehicle(brand, category, "Modele B " + RUN_ID, partnerB);

        partnerAToken = accessToken(userA.getEmail());
        partnerBToken = accessToken(userB.getEmail());
    }

    @Test
    void anonymousRequestsToPartnerPortalAreRejected() throws Exception {
        mockMvc.perform(get("/api/partner/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/partner/vehicles")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestsToAdminAreRejected() throws Exception {
        mockMvc.perform(get("/api/admin/partners")).andExpect(status().isUnauthorized());
    }

    @Test
    void partnerReadsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/partner/me").header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Partenaire A " + RUN_ID));
    }

    @Test
    void partnerListsOnlyOwnVehicles() throws Exception {
        mockMvc.perform(get("/api/partner/vehicles").header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(vehicleA.toString()));
    }

    @Test
    void partnerCanReadOwnVehicleById() throws Exception {
        mockMvc.perform(get("/api/partner/vehicles/" + vehicleA).header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicleA.toString()));
    }

    @Test
    void partnerCannotReadAnotherPartnersVehicleById() throws Exception {
        mockMvc.perform(get("/api/partner/vehicles/" + vehicleB).header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
    }

    @Test
    void partnerCannotLeakOtherVehicleInListing() throws Exception {
        String body = mockMvc.perform(get("/api/partner/vehicles").header("Authorization", bearer(partnerBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(vehicleA.toString()));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains(vehicleB.toString()));
    }

    @Test
    void unknownVehicleIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/partner/vehicles/" + UUID.randomUUID()).header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void partnerCannotAccessAdminPartnerRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/partners").header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/vehicles/" + vehicleB + "/partner-assignments")
                        .header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerCannotCreateOrModifyPartners() throws Exception {
        String body = "{\"name\":\"Intrus\",\"partnerType\":\"INDIVIDUAL\"}";
        mockMvc.perform(post("/api/admin/partners").with(csrf()).header("Authorization", bearer(partnerAToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/vehicles/" + vehicleA + "/partner-assignments").with(csrf())
                        .header("Authorization", bearer(partnerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerId\":\"" + partnerAId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerPortalIsReadOnly() throws Exception {
        mockMvc.perform(post("/api/partner/vehicles").with(csrf()).header("Authorization", bearer(partnerAToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void superAdminIsNotAllowedOnPartnerPortal() throws Exception {
        String adminToken = accessToken(TestProfileAdminSeeder.EMAIL);
        mockMvc.perform(get("/api/partner/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminStillReadsAdminPartnerRoutes() throws Exception {
        String adminToken = accessToken(TestProfileAdminSeeder.EMAIL);
        mockMvc.perform(get("/api/admin/partners").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousRequestsToGuaranteeRoutesAreRejected() throws Exception {
        mockMvc.perform(get("/api/partner/guarantees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/guarantees")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/guarantees").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerCannotAccessOrWriteAdminGuaranteeRoutes() throws Exception {
        UUID guaranteeId = seedGuarantee(vehicleA);
        mockMvc.perform(get("/api/admin/guarantees").header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/guarantees/" + guaranteeId).header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/guarantees").with(csrf()).header("Authorization", bearer(partnerAToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/guarantees/" + guaranteeId + "/withdraw").with(csrf())
                        .header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(GuaranteeStatus.ACTIVE,
                guarantees.findById(guaranteeId).orElseThrow().getStatus());
    }

    @Test
    void partnerReadsOnlyOwnGuarantees() throws Exception {
        UUID guaranteeA = seedGuarantee(vehicleA);
        UUID guaranteeB = seedGuarantee(vehicleB);

        String bodyA = mockMvc.perform(get("/api/partner/guarantees").header("Authorization", bearer(partnerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(bodyA.contains(guaranteeA.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains(guaranteeB.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(bodyA.contains(vehicleB.toString()));

        String bodyB = mockMvc.perform(get("/api/partner/guarantees").header("Authorization", bearer(partnerBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(bodyB.contains(guaranteeB.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(bodyB.contains(guaranteeA.toString()));
    }

    @Test
    void partnerGuaranteePortalIsReadOnly() throws Exception {
        mockMvc.perform(post("/api/partner/guarantees").with(csrf()).header("Authorization", bearer(partnerAToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void superAdminReadsAdminGuaranteeRoutes() throws Exception {
        UUID guaranteeId = seedGuarantee(vehicleA);
        String adminToken = accessToken(TestProfileAdminSeeder.EMAIL);
        mockMvc.perform(get("/api/admin/guarantees").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/guarantees/" + guaranteeId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(guaranteeId.toString()));
    }

    private UUID seedGuarantee(UUID vehicleId) {
        VehicleEntity vehicle = vehicles.findById(vehicleId).orElseThrow();
        UserEntity creator = users.findAll().get(0);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        GuaranteeEntity guarantee = new GuaranteeEntity(vehicle, vehicle.getPartner(), start,
                start.plus(2, ChronoUnit.DAYS), "Garantie test", creator, Instant.now());
        return guarantees.saveAndFlush(guarantee).getId();
    }

    private UserEntity createPartnerUser(String email, RoleEntity partnerRole) {
        UserEntity user = new UserEntity(email, passwordEncoder.encode(PASSWORD));
        user.addRole(partnerRole);
        return users.saveAndFlush(user);
    }

    private PartnerEntity linkPartner(String name, UserEntity user) {
        PartnerEntity partner = new PartnerEntity(name, PartnerType.COMPANY);
        partner.update(name, PartnerType.COMPANY, null, null, null, null, "TAX-" + UUID.randomUUID(), user);
        return partners.saveAndFlush(partner);
    }

    private UUID saveVehicle(BrandEntity brand, CategoryEntity category, String model, PartnerEntity partner) {
        VehicleEntity vehicle = new VehicleEntity(brand, category, model, 2024, "Test",
                BigDecimal.valueOf(200), BigDecimal.valueOf(1300), null, "TND", 5, 4,
                TransmissionType.AUTOMATIC, FuelType.PETROL, false, VehicleStatus.AVAILABLE);
        vehicle.setPartner(partner);
        return vehicles.saveAndFlush(vehicle).getId();
    }

    private String accessToken(String email) throws Exception {
        String password = email.equals(TestProfileAdminSeeder.EMAIL) ? TestProfileAdminSeeder.PASSWORD : PASSWORD;
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
