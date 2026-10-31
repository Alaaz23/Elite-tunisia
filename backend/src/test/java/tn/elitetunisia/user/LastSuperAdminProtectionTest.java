package tn.elitetunisia.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.common.config.TestProfileAdminSeeder;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.user.dto.CreateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateUserStatusRequest;
import tn.elitetunisia.user.service.AdminUserService;

/** Le dernier SUPER_ADMIN actif ne peut être ni désactivé ni rétrogradé ; chaque test s'exécute dans une transaction annulée. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LastSuperAdminProtectionTest {
    private static final String IP = "127.0.0.1";
    @Autowired AdminUserService service;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;

    private UUID seededActor() {
        return users.findByEmailIgnoreCase(TestProfileAdminSeeder.EMAIL).orElseThrow().getId();
    }

    private UUID newUser(String role) {
        String email = "lsa-" + UUID.randomUUID() + "@example.test";
        return service.create(new CreateEmployeeRequest("Test", "Admin", email, "+21612345678",
                "Passw0rd!Passw0rd", role, "Test"), seededActor(), IP).id();
    }

    private void makeSoleActiveSuperAdmin(UUID keep) {
        for (UserEntity user : users.findAll()) {
            if (!user.getId().equals(keep) && user.isEnabled() && user.getRoleKeys().contains("SUPER_ADMIN")) {
                user.setEnabled(false);
            }
        }
        users.flush();
    }

    private UpdateEmployeeRequest asRole(UUID id, String role) {
        UserEntity user = users.findById(id).orElseThrow();
        return new UpdateEmployeeRequest("Test", "Admin", user.getEmail(), "+21612345678", role, "Test");
    }

    private static void assertLastSuperAdmin(DomainException exception) {
        assertEquals("LAST_SUPER_ADMIN", exception.getCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void lastActiveSuperAdminCannotBeDisabled() {
        UUID sole = newUser("SUPER_ADMIN");
        makeSoleActiveSuperAdmin(sole);
        assertLastSuperAdmin(assertThrows(DomainException.class,
                () -> service.updateStatus(sole, new UpdateUserStatusRequest(false), sole, IP)));
        assertEquals(true, users.findById(sole).orElseThrow().isEnabled());
    }

    @Test
    void lastActiveSuperAdminCannotBeDemoted() {
        UUID sole = newUser("SUPER_ADMIN");
        makeSoleActiveSuperAdmin(sole);
        assertLastSuperAdmin(assertThrows(DomainException.class,
                () -> service.update(sole, asRole(sole, "MANAGER"), sole, IP)));
        assertEquals(Set.of("SUPER_ADMIN"), users.findById(sole).orElseThrow().getRoleKeys());
    }

    @Test
    void lastActiveSuperAdminCanStillBeUpdatedWithoutLosingTheRole() {
        UUID sole = newUser("SUPER_ADMIN");
        makeSoleActiveSuperAdmin(sole);
        assertDoesNotThrow(() -> service.update(sole, asRole(sole, "SUPER_ADMIN"), sole, IP));
        assertDoesNotThrow(() -> service.updateStatus(sole, new UpdateUserStatusRequest(true), sole, IP));
    }

    @Test
    void anotherActiveSuperAdminAllowsDemotion() {
        UUID first = newUser("SUPER_ADMIN");
        UUID second = newUser("SUPER_ADMIN");
        makeSoleActiveSuperAdmin(first);
        users.findById(second).orElseThrow().setEnabled(true);
        users.flush();
        assertDoesNotThrow(() -> service.update(second, asRole(second, "AGENT"), first, IP));
        assertEquals(Set.of("AGENT"), users.findById(second).orElseThrow().getRoleKeys());
        // il ne reste plus que « first » : il est de nouveau protégé
        assertLastSuperAdmin(assertThrows(DomainException.class,
                () -> service.updateStatus(first, new UpdateUserStatusRequest(false), first, IP)));
    }

    @Test
    void disabledSuperAdminDoesNotCountAsActive() {
        UUID sole = newUser("SUPER_ADMIN");
        UUID inactive = newUser("SUPER_ADMIN");
        makeSoleActiveSuperAdmin(sole);
        users.findById(inactive).orElseThrow().setEnabled(false);
        users.flush();
        assertLastSuperAdmin(assertThrows(DomainException.class,
                () -> service.updateStatus(sole, new UpdateUserStatusRequest(false), sole, IP)));
    }

    @Test
    void nonSuperAdminsAreNotAffected() {
        UUID agent = newUser("AGENT");
        assertDoesNotThrow(() -> service.updateStatus(agent, new UpdateUserStatusRequest(false), seededActor(), IP));
    }

    @Test
    void apiAnswersConflictWithAConsistentMessage() throws Exception {
        UUID sole = newUser("SUPER_ADMIN");
        makeSoleActiveSuperAdmin(sole);
        String email = users.findById(sole).orElseThrow().getEmail();
        mvc.perform(patch("/api/admin/users/" + sole + "/status")
                .with(SecurityMockMvcRequestPostProcessors.user(email).authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("admin:access"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("users:read"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("users:manage")))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_SUPER_ADMIN"));
    }
}
