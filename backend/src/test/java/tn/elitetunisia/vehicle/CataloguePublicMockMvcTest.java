package tn.elitetunisia.vehicle;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import tn.elitetunisia.brand.BrandEntity;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.category.CategoryEntity;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.common.config.TestProfileAdminSeeder;

/**
 * Tests MockMvc du catalogue public (visibilité, filtres, tri, pagination) et du passage
 * à ARCHIVED via l'API admin. Chaîne HTTP réelle : filtres Spring Security et contrôleurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CataloguePublicMockMvcTest {
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    @Autowired private MockMvc mockMvc;
    @Autowired private BrandRepository brands;
    @Autowired private CategoryRepository categories;
    @Autowired private VehicleRepository vehicles;

    private BrandEntity brand;
    private CategoryEntity category;

    @BeforeEach
    void setUp() {
        brand = brands.saveAndFlush(new BrandEntity("Marque " + RUN_ID, "marque-" + RUN_ID, "Test", null, true));
        category = categories.saveAndFlush(new CategoryEntity("Cat " + RUN_ID, "cat-" + RUN_ID, "Test", true));
    }

    private UUID save(String model, BigDecimal price, VehicleStatus status, BrandEntity vehicleBrand) {
        VehicleEntity vehicle = new VehicleEntity(vehicleBrand, category, model, 2024, "Test",
                price, price.multiply(BigDecimal.valueOf(6)), null, "TND", 5, 4,
                TransmissionType.AUTOMATIC, FuelType.PETROL, false, status);
        return vehicles.saveAndFlush(vehicle).getId();
    }

    private UUID save(String model, BigDecimal price, VehicleStatus status) {
        return save(model, price, status, brand);
    }

    private String brandFilter() {
        return "/api/vehicles?brand=marque-" + RUN_ID + "&size=50";
    }

    @Test
    void anonymousCatalogueExposesOnlyAvailableVehicles() throws Exception {
        UUID available = save("Visible " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.AVAILABLE);
        save("Retire " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.UNAVAILABLE);
        save("Maintenance " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.MAINTENANCE);
        save("Archive " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.ARCHIVED);
        save("Reserve " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.RENTED);

        mockMvc.perform(get(brandFilter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(available.toString()))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"));
    }

    @Test
    void publicDetailReturnsAvailableVehicleAndHidesArchivedOne() throws Exception {
        UUID available = save("Detail " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.AVAILABLE);
        UUID archived = save("Archived " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.ARCHIVED);

        mockMvc.perform(get("/api/vehicles/" + available)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(available.toString()));
        mockMvc.perform(get("/api/vehicles/" + archived)).andExpect(status().isNotFound());
    }

    @Test
    void vehicleWithInactiveBrandIsNotPublic() throws Exception {
        BrandEntity inactive = brands.saveAndFlush(new BrandEntity("Inactive " + RUN_ID, "inactive-" + RUN_ID, "Test", null, false));
        UUID hidden = save("Hidden " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.AVAILABLE, inactive);

        mockMvc.perform(get("/api/vehicles?brand=inactive-" + RUN_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/vehicles/" + hidden)).andExpect(status().isNotFound());
    }

    @Test
    void filtersSortingAndPaginationAreApplied() throws Exception {
        save("Cheap " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.AVAILABLE);
        save("Mid " + RUN_ID, BigDecimal.valueOf(200), VehicleStatus.AVAILABLE);
        save("Expensive " + RUN_ID, BigDecimal.valueOf(300), VehicleStatus.AVAILABLE);

        mockMvc.perform(get(brandFilter() + "&sort=price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].pricePerDay").value(100))
                .andExpect(jsonPath("$.content[2].pricePerDay").value(300));

        mockMvc.perform(get(brandFilter() + "&sort=price_desc"))
                .andExpect(jsonPath("$.content[0].pricePerDay").value(300));

        mockMvc.perform(get(brandFilter() + "&minPrice=150&maxPrice=250"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pricePerDay").value(200));

        mockMvc.perform(get("/api/vehicles?brand=marque-" + RUN_ID + "&sort=price_asc&size=2&page=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].pricePerDay").value(300));
    }

    @Test
    void invalidSortCriterionIsRejected() throws Exception {
        mockMvc.perform(get(brandFilter() + "&sort=drop_table")).andExpect(status().isBadRequest());
    }

    @Test
    void invalidPriceRangeIsRejected() throws Exception {
        mockMvc.perform(get(brandFilter() + "&minPrice=300&maxPrice=100")).andExpect(status().isBadRequest());
    }

    @Test
    void adminArchiveRemovesVehicleFromPublicCatalogueAndRequiresAuthentication() throws Exception {
        UUID vehicle = save("Archivable " + RUN_ID, BigDecimal.valueOf(100), VehicleStatus.AVAILABLE);
        String url = "/api/admin/vehicles/" + vehicle + "/status";

        mockMvc.perform(patch(url).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isUnauthorized());

        String adminToken = accessToken(TestProfileAdminSeeder.EMAIL);
        mockMvc.perform(patch(url).header("Authorization", "Bearer " + adminToken).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/vehicles/" + vehicle)).andExpect(status().isNotFound());
        mockMvc.perform(get(brandFilter())).andExpect(jsonPath("$.totalElements").value(0));
    }

    private String accessToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, TestProfileAdminSeeder.PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
