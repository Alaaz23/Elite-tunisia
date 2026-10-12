package tn.elitetunisia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import tn.elitetunisia.auth.entity.RoleEntity;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.customer.CustomerRepository;
import tn.elitetunisia.employee.EmployeeEntity;
import tn.elitetunisia.employee.EmployeeRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {
    private static final String PASSWORD = "Strong-Pass-123!";
    private static final String REFRESH_COOKIE = "elite_refresh";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private CustomerRepository customers;
    @Autowired private EmployeeRepository employees;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testProfileSeedsAnInternalSuperAdmin() {
        UserEntity admin = users.findByEmailIgnoreCase(tn.elitetunisia.common.config.TestProfileAdminSeeder.EMAIL).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(admin.getRoleKeys().contains("SUPER_ADMIN"));
        org.junit.jupiter.api.Assertions.assertFalse(admin.getRoleKeys().contains("CUSTOMER"));
        org.junit.jupiter.api.Assertions.assertTrue(employees.findById(admin.getId()).isPresent());
    }

    @Test
    void seededSuperAdminCanAuthenticate() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(login(tn.elitetunisia.common.config.TestProfileAdminSeeder.EMAIL,
                            tn.elitetunisia.common.config.TestProfileAdminSeeder.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"));
    }

    @Test
        void registrationCreatesCustomerProfileAndDoesNotExposeSecrets() throws Exception {
        String refreshToken = register("customer@example.test", "Amira", "Ben Salem");
        String accessToken = accessToken("customer@example.test");

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("customer@example.test"))
            .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
            .andExpect(jsonPath("$.firstName").value("Amira"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        UserEntity user = users.findByEmailIgnoreCase("customer@example.test").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));
        org.junit.jupiter.api.Assertions.assertEquals("CUSTOMER", user.getRoleKeys().iterator().next());
        org.junit.jupiter.api.Assertions.assertTrue(customers.findById(user.getId()).isPresent());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND token_hash = ?",
            Integer.class, user.getId(), sha256(refreshToken)));

        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registration("customer@example.test", "Amira", "Ben Salem", PASSWORD)))
            .andExpect(status().isConflict());
        }

        @Test
        void registrationValidatesAllFieldsAndPasswordConfirmation() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());

        mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registration("mismatch@example.test", "A", "B", "Different-Pass-456!")))
            .andExpect(status().isBadRequest());

        MvcResult roleChoice = mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"role-choice@example.test\",\"firstName\":\"A\",\"lastName\":\"B\",\"phone\":\"+216 20 123 456\",\"password\":\"Strong-Pass-123!\",\"confirmPassword\":\"Strong-Pass-123!\",\"role\":\"SUPER_ADMIN\"}"))
            .andExpect(status().isCreated()).andReturn();
        String roleChoiceId = JsonPath.read(roleChoice.getResponse().getContentAsString(), "$.userId");
        org.junit.jupiter.api.Assertions.assertEquals("CUSTOMER", users.findByEmailIgnoreCase("role-choice@example.test").orElseThrow().getRoleKeys().iterator().next());
    }

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        register("denied@example.test", "Sami", "Ali");
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Adresse e-mail ou mot de passe incorrect."));

        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(login("denied@example.test", "Wrong-Pass-123!")))
            .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN' AND result = 'FAILURE'", Integer.class) > 0);
    }

    @Test
        void unauthenticatedAndCustomerRequestsCannotEnterProtectedAreas() throws Exception {
        mockMvc.perform(get("/api/customer/profile")).andExpect(status().isUnauthorized());
            register("guarded@example.test", "Guarded", "Customer");
            String customerAccess = accessToken("guarded@example.test");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(customerAccess)))
            .andExpect(status().isForbidden());
        }

        @Test
        void customerCanOnlyEditTheirOwnProfile() throws Exception {
        register("first@example.test", "First", "Customer");
        register("second@example.test", "Second", "Customer");
        String secondAccess = accessToken("second@example.test");
        UserEntity first = users.findByEmailIgnoreCase("first@example.test").orElseThrow();

        mockMvc.perform(put("/api/customer/profile").with(csrf()).header("Authorization", bearer(secondAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Updated\",\"lastName\":\"Customer\",\"phone\":\"+216 20 123 456\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Updated"));
        mockMvc.perform(put("/api/customer/profile/{id}", first.getId()).with(csrf())
                .header("Authorization", bearer(secondAccess)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Hijack\",\"lastName\":\"Attempt\",\"phone\":\"+216 20 123 456\"}"))
            .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals("First", customers.findById(first.getId()).orElseThrow().getFirstName());
        }

        @Test
        void refreshRotationReuseLogoutExpiryAndDisabledUsersAreRejected() throws Exception {
        String firstRefresh = register("refresh@example.test", "Rami", "User");
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, "invalid-random-refresh-token")))
            .andExpect(status().isUnauthorized());
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, firstRefresh)))
            .andExpect(status().isOk()).andExpect(cookie().httpOnly(REFRESH_COOKIE, true)).andReturn();
        String secondRefresh = refreshed.getResponse().getCookie(REFRESH_COOKIE).getValue();
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, firstRefresh)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, secondRefresh)))
            .andExpect(status().isUnauthorized());

        String logoutRefresh = register("logout@example.test", "Lina", "User");
        mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, logoutRefresh)))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, logoutRefresh)))
            .andExpect(status().isUnauthorized());

        String expiredRefresh = register("expired@example.test", "Elyes", "User");
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
            Timestamp.from(Instant.now().minusSeconds(1)), sha256(expiredRefresh));
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, expiredRefresh)))
            .andExpect(status().isUnauthorized());

        String disabledRefresh = register("disabled@example.test", "Maya", "User");
        String disabledAccess = accessToken("disabled@example.test");
        UserEntity disabled = users.findByEmailIgnoreCase("disabled@example.test").orElseThrow();
        disabled.setEnabled(false);
        users.saveAndFlush(disabled);
        mockMvc.perform(get("/api/customer/profile").header("Authorization", bearer(disabledAccess)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new MockCookie(REFRESH_COOKIE, disabledRefresh)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(login("disabled@example.test", PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Votre compte a été désactivé."));
        }

        @Test
        void superAdminCanCreateUpdateAndDisableAnEmployee() throws Exception {
        createEmployee("admin@example.test", "SUPER_ADMIN");
        String adminAccess = accessToken("admin@example.test");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminAccess)))
            .andExpect(status().isOk());

        MvcResult created = mockMvc.perform(post("/api/admin/users").with(csrf())
                .header("Authorization", bearer(adminAccess)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Agent\",\"lastName\":\"One\",\"email\":\"agent@example.test\",\"phone\":\"+216 20 123 456\",\"password\":\"Agent-Password-123!\",\"role\":\"AGENT\",\"jobTitle\":\"Agent\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("AGENT")).andReturn();
        UUID agentId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(put("/api/admin/users/{id}", agentId).with(csrf())
                .header("Authorization", bearer(adminAccess)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Agent\",\"lastName\":\"One\",\"email\":\"agent@example.test\",\"phone\":\"+216 20 123 456\",\"role\":\"ACCOUNTANT\",\"jobTitle\":\"Comptable\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ACCOUNTANT"));
        mockMvc.perform(patch("/api/admin/users/{id}/status", agentId).with(csrf())
                .header("Authorization", bearer(adminAccess)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(login("agent@example.test", "Agent-Password-123!")))
            .andExpect(status().isUnauthorized());
        }

        @Test
        void catalogueCrudFiltersSortingPaginationAndCustomerAuthorization() throws Exception {
        String adminEmail = "catalogue-admin-" + UUID.randomUUID() + "@example.test";
        createEmployee(adminEmail, "SUPER_ADMIN");
        String adminAccess = accessToken(adminEmail);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String brandName = "Test Brand " + suffix;
        String brandSlug = brandName.toLowerCase().replace(' ', '-');

        MvcResult brandResult = mockMvc.perform(post("/api/admin/brands").with(csrf())
                .header("Authorization", bearer(adminAccess)).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"%s\",\"description\":\"Test brand\"}", brandName)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value(brandSlug)).andReturn();
        UUID brandId = UUID.fromString(JsonPath.read(brandResult.getResponse().getContentAsString(), "$.id"));
        mockMvc.perform(post("/api/admin/brands").with(csrf()).header("Authorization", bearer(adminAccess))
                .contentType(MediaType.APPLICATION_JSON).content(String.format("{\"name\":\"%s\",\"description\":\"Duplicate\"}", brandName)))
            .andExpect(status().isConflict());

        String categoryName = "Test Category " + suffix;
        MvcResult categoryResult = mockMvc.perform(post("/api/admin/categories").with(csrf())
                .header("Authorization", bearer(adminAccess)).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"%s\",\"description\":\"Test category\"}", categoryName)))
            .andExpect(status().isOk()).andReturn();
        UUID categoryId = UUID.fromString(JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.id"));

        UUID firstVehicle = createCatalogueVehicle(adminAccess, brandId, categoryId, "Alpha " + suffix, "75.000");
        UUID secondVehicle = createCatalogueVehicle(adminAccess, brandId, categoryId, "Bravo " + suffix, "45.000");
        mockMvc.perform(put("/api/admin/brands/{id}", brandId).with(csrf()).header("Authorization", bearer(adminAccess))
                .contentType(MediaType.APPLICATION_JSON).content(String.format("{\"name\":\"%s\",\"description\":\"Updated brand\",\"active\":true}", brandName)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated brand"));
        mockMvc.perform(put("/api/admin/categories/{id}", categoryId).with(csrf()).header("Authorization", bearer(adminAccess))
                .contentType(MediaType.APPLICATION_JSON).content(String.format("{\"name\":\"%s\",\"description\":\"Updated category\",\"active\":true}", categoryName)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated category"));
        mockMvc.perform(put("/api/admin/vehicles/{id}", secondVehicle).with(csrf()).header("Authorization", bearer(adminAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"brandId\":\"%s\",\"categoryId\":\"%s\",\"model\":\"Bravo Updated %s\",\"year\":2024,\"description\":\"Updated vehicle\",\"pricePerDay\":48,\"pricePerWeek\":340,\"currency\":\"TND\",\"passengers\":5,\"doors\":4,\"transmission\":\"AUTOMATIC\",\"fuel\":\"PETROL\",\"withDriverAvailable\":false,\"features\":[\"GPS\"]}", brandId, categoryId, suffix)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("Bravo Updated " + suffix))
            .andExpect(jsonPath("$.pricePerDay").value(48.0)).andExpect(jsonPath("$.features[0]").value("GPS"));
        mockMvc.perform(post("/api/admin/vehicles").with(csrf()).header("Authorization", bearer(adminAccess))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"brandId\":\"%s\",\"categoryId\":\"%s\",\"model\":\"Invalid %s\",\"year\":2024,\"description\":\"Invalid price\",\"pricePerDay\":0,\"pricePerWeek\":350,\"currency\":\"TND\",\"passengers\":5,\"doors\":4,\"transmission\":\"AUTOMATIC\",\"fuel\":\"PETROL\",\"withDriverAvailable\":false}", brandId, categoryId, suffix)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.pricePerDay").exists());
        mockMvc.perform(get("/api/vehicles").param("brand", brandSlug).param("sort", "price_asc")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].pricePerDay").value(48.0))
            .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/vehicles").param("minPrice", "90").param("maxPrice", "20"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vehicles").param("sort", "password"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/vehicles").param("minPrice", "-1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/admin/brands/{id}", brandId).with(csrf())
                .header("Authorization", bearer(adminAccess)))
            .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/admin/vehicles/{id}/status", firstVehicle).with(csrf())
                .header("Authorization", bearer(adminAccess)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"MAINTENANCE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MAINTENANCE"));
        mockMvc.perform(get("/api/vehicles/{id}", firstVehicle)).andExpect(status().isNotFound());

        String customerEmail = "catalogue-customer-" + suffix + "@example.test";
        register(customerEmail, "Catalogue", "Customer");
        String customerToken = accessToken(customerEmail);
        mockMvc.perform(get("/api/admin/vehicles").header("Authorization", bearer(customerToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/vehicles").with(csrf()).header("Authorization", bearer(customerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/admin/vehicles/{id}", firstVehicle).with(csrf())
                .header("Authorization", bearer(adminAccess)))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/vehicles/{id}", secondVehicle).with(csrf())
                .header("Authorization", bearer(adminAccess)))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/brands/{id}", brandId).with(csrf())
                .header("Authorization", bearer(adminAccess)))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/categories/{id}", categoryId).with(csrf())
                .header("Authorization", bearer(adminAccess)))
            .andExpect(status().isNoContent());
        }

        @Test
        void uploadedImageSurvivesRepeatedVehicleUpdatesAndIsDeletedWithVehicle() throws Exception {
        String email = "image-admin-" + UUID.randomUUID() + "@example.test";
        createEmployee(email, "SUPER_ADMIN");
        String token = accessToken(email);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult brand = mockMvc.perform(post("/api/admin/brands").with(csrf()).header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Image Brand " + suffix + "\",\"description\":\"Image test\"}"))
            .andExpect(status().isOk()).andReturn();
        MvcResult category = mockMvc.perform(post("/api/admin/categories").with(csrf()).header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Image Category " + suffix + "\",\"description\":\"Image test\"}"))
            .andExpect(status().isOk()).andReturn();
        String brandId = JsonPath.read(brand.getResponse().getContentAsString(), "$.id");
        String categoryId = JsonPath.read(category.getResponse().getContentAsString(), "$.id");
        MockMultipartFile png = new MockMultipartFile("file", "image.png", "image/png",
            java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ioAAAAASUVORK5CYII="));
        MvcResult uploaded = mockMvc.perform(multipart("/api/admin/uploads/images").file(png).with(csrf())
            .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn();
        String url = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.url");
        mockMvc.perform(get(url)).andExpect(status().isOk());
        String input = String.format("{\"brandId\":\"%s\",\"categoryId\":\"%s\",\"model\":\"Photo\",\"year\":2025,\"description\":\"Photo test\",\"pricePerDay\":125.123,\"pricePerWeek\":800,\"currency\":\"TND\",\"passengers\":5,\"doors\":4,\"transmission\":\"AUTOMATIC\",\"fuel\":\"PETROL\",\"withDriverAvailable\":false,\"features\":[\"GPS\"],\"images\":[{\"url\":\"%s\",\"altText\":\"Photo test\",\"position\":0,\"isPrimary\":true}]}", brandId, categoryId, url);
        MvcResult created = mockMvc.perform(post("/api/admin/vehicles").with(csrf()).header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk()).andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        for (int iteration = 0; iteration < 2; iteration++) {
            mockMvc.perform(put("/api/admin/vehicles/{id}", id).with(csrf()).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isOk());
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
        mockMvc.perform(delete("/api/admin/uploads/images/{key}", url.substring("/api/files/".length())).with(csrf())
            .header("Authorization", bearer(token))).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/vehicles/{id}", id).with(csrf()).header("Authorization", bearer(token)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        }

        private UUID createCatalogueVehicle(String token, UUID brandId, UUID categoryId, String model, String price) throws Exception {
        String json = String.format("{\"brandId\":\"%s\",\"categoryId\":\"%s\",\"model\":\"%s\",\"year\":2024,\"description\":\"Test vehicle\",\"pricePerDay\":%s,\"pricePerWeek\":350,\"currency\":\"TND\",\"passengers\":5,\"doors\":4,\"transmission\":\"AUTOMATIC\",\"fuel\":\"PETROL\",\"withDriverAvailable\":false,\"features\":[\"Climatisation\"]}",
                brandId, categoryId, model, price);
        MvcResult result = mockMvc.perform(post("/api/admin/vehicles").with(csrf()).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andExpect(jsonPath("$.features[0]").value("Climatisation")).andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        }

        private String register(String email, String firstName, String lastName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registration(email, firstName, lastName, PASSWORD)))
            .andExpect(status().isCreated()).andExpect(cookie().httpOnly(REFRESH_COOKIE, true)).andReturn();
        return result.getResponse().getCookie(REFRESH_COOKIE).getValue();
        }

        private String accessToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(login(email, PASSWORD)))
            .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        }

        private UserEntity createEmployee(String email, String roleKey) {
        RoleEntity role = roles.findById(roleKey).orElseThrow();
        UserEntity user = new UserEntity(email, passwordEncoder.encode(PASSWORD));
        user.addRole(role);
        user = users.saveAndFlush(user);
        employees.saveAndFlush(new EmployeeEntity(user, "EMP-" + UUID.randomUUID().toString().substring(0, 8),
            "Test", "Admin", "User", "+216 20 123 456"));
        return user;
        }

        private String registration(String email, String firstName, String lastName, String confirmPassword) throws Exception {
        return String.format("{\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\",\"phone\":\"+216 20 123 456\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}",
            email, firstName, lastName, PASSWORD, confirmPassword);
        }

        private String login(String email, String password) throws Exception {
        return String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        }

        private String bearer(String token) { return "Bearer " + token; }

        private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
