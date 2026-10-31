package tn.elitetunisia.quote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.employee.EmployeeEntity;
import tn.elitetunisia.employee.EmployeeRepository;

/** Règles métier, validation et droits du catalogue de services (H2 par défaut, PostgreSQL si demandé). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServiceCatalogWorkflowTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired ServiceCatalogService service;
    @Autowired ConfiguredServiceRepository repository;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired EmployeeRepository employees;
    @Autowired MockMvc mvc;

    private static String unique(String prefix) { return prefix + " " + UUID.randomUUID().toString().substring(0, 8); }

    private UUID staff() {
        var user = new UserEntity("svc-staff-" + UUID.randomUUID() + "@example.test", "not-a-login-password");
        user.addRole(roles.findById("MANAGER").orElseThrow());
        users.saveAndFlush(user);
        employees.saveAndFlush(new EmployeeEntity(user, "EMP-" + UUID.randomUUID().toString().substring(0, 8), "Manager", "Svc", "Staff", "+216 20 000 000"));
        return user.getId();
    }

    private RequestPostProcessor caller(String... authorities) {
        var user = new UserEntity("svc-http-" + UUID.randomUUID() + "@example.test", "not-a-login-password");
        user.addRole(roles.findById("MANAGER").orElseThrow());
        users.saveAndFlush(user);
        employees.saveAndFlush(new EmployeeEntity(user, "EMP-" + UUID.randomUUID().toString().substring(0, 8), "Manager", "Svc", "Http", "+216 20 000 000"));
        var granted = new java.util.ArrayList<SimpleGrantedAuthority>();
        granted.add(new SimpleGrantedAuthority("admin:access"));
        for (String a : authorities) granted.add(new SimpleGrantedAuthority(a));
        return SecurityMockMvcRequestPostProcessors.user(user.getEmail()).authorities(granted);
    }

    private static void expect(String code, Runnable action) {
        assertEquals(code, assertThrows(DomainException.class, action::run).getCode());
    }

    @Test
    void createdServiceIsActiveAndAppearsInQuoteOptionsUntilDeactivated() {
        UUID actor = staff();
        String name = unique("Siège enfant");
        var created = service.create(new ServiceCreateRequest("  " + name + "  ", " Siège homologué ", new BigDecimal("12.500"), "TND"), actor, null);
        assertTrue(created.isActive());
        assertEquals(name, created.getName());
        assertEquals("Siège homologué", created.getDescription());
        assertTrue(repository.findByActiveTrueAndCurrencyOrderByNameAsc("TND").stream().anyMatch(s -> s.getId().equals(created.getId())));

        service.setActive(created.getId(), false, actor, null);
        assertFalse(repository.findByActiveTrueAndCurrencyOrderByNameAsc("TND").stream().anyMatch(s -> s.getId().equals(created.getId())));
        assertTrue(repository.findById(created.getId()).isPresent(), "un service désactivé n'est jamais supprimé");

        service.setActive(created.getId(), true, actor, null);
        assertTrue(service.get(created.getId()).isActive());
    }

    @Test
    void activeNamesAreUniquePerCurrencyIgnoringCase() {
        UUID actor = staff();
        String name = unique("Wifi");
        var first = service.create(new ServiceCreateRequest(name, "", new BigDecimal("5"), "TND"), actor, null);
        expect("SERVICE_NAME_TAKEN", () -> service.create(new ServiceCreateRequest(name.toUpperCase(), "", new BigDecimal("6"), "TND"), actor, null));
        service.create(new ServiceCreateRequest(name, "", new BigDecimal("6"), "EUR"), actor, null);

        service.setActive(first.getId(), false, actor, null);
        var replacement = service.create(new ServiceCreateRequest(name, "", new BigDecimal("7"), "TND"), actor, null);
        expect("SERVICE_NAME_TAKEN", () -> service.setActive(first.getId(), true, actor, null));
        assertTrue(replacement.isActive());
    }

    @Test
    void updateChangesDetailsButNeverCurrencyOrActiveState() {
        UUID actor = staff();
        var created = service.create(new ServiceCreateRequest(unique("Chauffeur"), "", new BigDecimal("80"), "TND"), actor, null);
        var updated = service.update(created.getId(), new ServiceUpdateRequest(unique("Chauffeur premium"), "Détail", new BigDecimal("95.250")), actor, null);
        assertEquals(new BigDecimal("95.250"), updated.getPrice());
        assertEquals("TND", updated.getCurrency());
        assertTrue(updated.isActive());
        expect("SERVICE_NOT_FOUND", () -> service.update(UUID.randomUUID(), new ServiceUpdateRequest("Valid name", "", BigDecimal.ONE), actor, null));
    }

    @Test
    void listFiltersBySearchAndActiveState() {
        UUID actor = staff();
        String tag = unique("Filtre");
        var active = service.create(new ServiceCreateRequest(tag + " actif", "", BigDecimal.ONE, "TND"), actor, null);
        var inactive = service.create(new ServiceCreateRequest(tag + " inactif", "", BigDecimal.ONE, "TND"), actor, null);
        service.setActive(inactive.getId(), false, actor, null);
        assertEquals(2, service.list(tag.toLowerCase(), null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, service.list(tag, true, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(active.getId(), service.list(tag, true, PageRequest.of(0, 10)).getContent().get(0).getId());
        assertEquals(inactive.getId(), service.list(tag, false, PageRequest.of(0, 10)).getContent().get(0).getId());
    }

    // ---- Couche HTTP ----

    private static String json(String name, String price, String currency) {
        return String.format("{\"name\":\"%s\",\"description\":\"d\",\"price\":%s%s}", name, price, currency == null ? "" : ",\"currency\":\"" + currency + "\"");
    }

    @Test
    void invalidBodiesAreRejectedWith400() throws Exception {
        for (String body : new String[] { "{}", json("x", "5", "TND"), json("Valid name", "0", "TND"), json("Valid name", "-3", "TND"),
                json("Valid name", "1.2345", "TND"), json("Valid name", "5", "tnd"), json("Valid name", "5", "TNDX"), json("Valid name", "5", null) }) {
            mvc.perform(post("/api/admin/services").with(caller("services:manage")).with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void forgedIdentifierActiveStateAndCurrencyInUpdateAreIgnored() throws Exception {
        UUID actor = staff();
        var created = service.create(new ServiceCreateRequest(unique("Forge"), "", new BigDecimal("10"), "TND"), actor, null);
        String body = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"d\",\"price\":11,\"currency\":\"EUR\",\"active\":false}", UUID.randomUUID(), unique("Forge bis"));
        mvc.perform(put("/api/admin/services/" + created.getId()).with(caller("services:manage")).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.currency").value("TND")).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void readOnlyStaffCanListButNotWrite() throws Exception {
        UUID actor = staff();
        var created = service.create(new ServiceCreateRequest(unique("Lecture"), "", BigDecimal.TEN, "TND"), actor, null);
        RequestPostProcessor reader = caller("services:read");
        mvc.perform(get("/api/admin/services").with(reader)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/services/" + created.getId()).with(reader)).andExpect(status().isOk());
        // Corps invalide volontaire : le refus doit être 403 avant toute validation (aucun indice sur le contrat).
        mvc.perform(post("/api/admin/services").with(reader).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/services/" + created.getId()).with(reader).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/services/" + created.getId() + "/deactivate").with(reader).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
        assertTrue(service.get(created.getId()).isActive());
    }

    @Test
    void anonymousCustomersAndCsrfAreEnforced() throws Exception {
        mvc.perform(get("/api/admin/services")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/services").with(SecurityMockMvcRequestPostProcessors.user("client@example.test").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/services").with(caller("services:manage")).contentType(MediaType.APPLICATION_JSON).content(json("Valid name", "5", "TND")))
                .andExpect(status().isForbidden());
    }

    @Test
    void domainErrorsAreTypedResponses() throws Exception {
        mvc.perform(get("/api/admin/services/" + UUID.randomUUID()).with(caller("services:read")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
        String name = unique("Doublon");
        RequestPostProcessor manager = caller("services:manage");
        mvc.perform(post("/api/admin/services").with(manager).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(name, "5", "TND"))).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/services").with(manager).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(name, "6", "TND")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVICE_NAME_TAKEN"));
        mvc.perform(get("/api/admin/services").param("size", "101").with(caller("services:read"))).andExpect(status().isBadRequest());
    }
}
