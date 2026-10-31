package tn.elitetunisia.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.user.dto.CreateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateUserStatusRequest;
import tn.elitetunisia.user.service.AdminUserService;

/** Deux administrateurs actifs ne peuvent pas se désactiver ou se rétrograder mutuellement en parallèle. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
@Tag("postgres")
class LastSuperAdminConcurrencyIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired AdminUserService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    private UUID newSuperAdmin() {
        String email = "concurrent-" + UUID.randomUUID() + "@example.test";
        return service.create(new CreateEmployeeRequest("Test", "Admin", email, "+21612345678",
                "Passw0rd!Passw0rd", "SUPER_ADMIN", "Test"), null, "127.0.0.1").id();
    }

    private int activeSuperAdmins() {
        return jdbc.queryForObject("""
                select count(*) from users u join user_roles r on r.user_id = u.id
                where r.role_key = 'SUPER_ADMIN' and u.enabled = true""", Integer.class);
    }

    private List<UUID> deactivateOtherSuperAdmins(UUID... keep) {
        List<UUID> others = jdbc.queryForList("""
                select u.id from users u join user_roles r on r.user_id = u.id
                where r.role_key = 'SUPER_ADMIN' and u.enabled = true""", UUID.class);
        others.removeAll(List.of(keep));
        others.forEach(id -> jdbc.update("update users set enabled = false where id = ?", id));
        return others;
    }

    private void restore(List<UUID> ids) {
        ids.forEach(id -> jdbc.update("update users set enabled = true where id = ?", id));
    }

    /** Lance les deux opérations au même instant ; retourne le nombre de succès et vérifie que les autres échouent par LAST_SUPER_ADMIN. */
    private int runConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = List.of(first, second).stream().map(task -> pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            })).toList();
            ready.await();
            go.countDown();
            int successes = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    successes++;
                } catch (java.util.concurrent.ExecutionException e) {
                    DomainException refused = (DomainException) e.getCause();
                    assertEquals("LAST_SUPER_ADMIN", refused.getCode());
                }
            }
            return successes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void simultaneousDisablingKeepsOneActiveSuperAdmin() throws Exception {
        UUID a = newSuperAdmin();
        UUID b = newSuperAdmin();
        List<UUID> others = deactivateOtherSuperAdmins(a, b);
        try {
            for (int round = 0; round < 5; round++) {
                jdbc.update("update users set enabled = true where id in (?, ?)", a, b);
                int successes = runConcurrently(
                        () -> service.updateStatus(a, new UpdateUserStatusRequest(false), null, "127.0.0.1"),
                        () -> service.updateStatus(b, new UpdateUserStatusRequest(false), null, "127.0.0.1"));
                assertEquals(1, successes, "round " + round);
                assertEquals(1, activeSuperAdmins(), "round " + round);
            }
        } finally {
            restore(others);
        }
    }

    @Test
    void simultaneousDemotionKeepsOneActiveSuperAdmin() throws Exception {
        UUID a = newSuperAdmin();
        UUID b = newSuperAdmin();
        List<UUID> others = deactivateOtherSuperAdmins(a, b);
        try {
            int successes = runConcurrently(() -> demote(a), () -> demote(b));
            assertEquals(1, successes);
            assertEquals(1, activeSuperAdmins());
        } finally {
            restore(others);
        }
    }

    private Object demote(UUID id) {
        String email = jdbc.queryForObject("select email from users where id = ?", String.class, id);
        return service.update(id, new UpdateEmployeeRequest("Test", "Admin", email, "+21612345678", "AGENT", "Test"),
                null, "127.0.0.1");
    }
}
