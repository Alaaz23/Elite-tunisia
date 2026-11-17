package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.elitetunisia.PostgresTestHelper;

/**
 * Verrou advisory du planificateur de réconciliation sur PostgreSQL réel (elite_a4_test).
 * La réconciliation est remplacée par un double pour contrôler précisément le point d'interruption ;
 * le verrou, lui, est réel. Les sondes passent par une autre connexion, sans threads ni délais.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
@Tag("postgres")
public class PaymentReconciliationWorkerLockPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final String EXPECTED_DB = "elite_a4_test";
    private static final Duration OLDER = Duration.ofMinutes(15);

    @Autowired JdbcTemplate jdbc;

    private void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
        assertEquals(EXPECTED_DB, jdbc.queryForObject("SELECT current_database()", String.class));
    }

    /** Vrai si la clé est libre pour une autre session ; la sonde libère immédiatement si elle l'obtient. */
    private boolean isLockFreeFromOtherSession() throws Exception {
        try (Connection probe = jdbc.getDataSource().getConnection()) {
            boolean free = queryBoolean(probe, "select pg_try_advisory_lock(?)");
            if (free) {
                queryBoolean(probe, "select pg_advisory_unlock(?)");
            }
            return free;
        }
    }

    private static boolean queryBoolean(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, PaymentReconciliationWorker.LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private PaymentReconciliationWorker worker(PaymentReconciliationService reconciliation) {
        return new PaymentReconciliationWorker(jdbc.getDataSource(), reconciliation, OLDER, 50);
    }

    @Test
    public void lockIsHeldWhileRunningAndRefusesConcurrentRun() throws Exception {
        requirePostgres();
        var reconciliation = mock(PaymentReconciliationService.class);
        var worker = worker(reconciliation);
        var nestedReports = new java.util.ArrayList<PaymentReconciliationWorker.RunReport>();
        when(reconciliation.reconcileBatch(any(), anyInt())).thenAnswer(inv -> {
            assertTrue(!isLockFreeFromOtherSession(), "Le verrou doit être détenu pendant le traitement");
            nestedReports.add(worker.runOnce(Instant.now()));
            return List.of();
        });

        var report = worker.runOnce(Instant.now());

        assertEquals(0, report.skippedBecauseLocked());
        assertEquals(1, nestedReports.size());
        assertEquals(1, nestedReports.get(0).skippedBecauseLocked(), "Une seconde exécution concurrente doit être refusée");
        assertEquals(0, nestedReports.get(0).processed(), "L'exécution refusée ne traite aucun élément");
    }

    @Test
    public void lockIsReleasedAfterNormalRun() throws Exception {
        requirePostgres();
        var reconciliation = mock(PaymentReconciliationService.class);
        when(reconciliation.reconcileBatch(any(), anyInt())).thenReturn(List.of());

        worker(reconciliation).runOnce(Instant.now());

        assertTrue(isLockFreeFromOtherSession(), "Aucun verrou orphelin après exécution normale");
    }

    @Test
    public void lockIsReleasedAfterUnexpectedRuntimeFailure() throws Exception {
        requirePostgres();
        var reconciliation = mock(PaymentReconciliationService.class);
        when(reconciliation.reconcileBatch(any(), anyInt())).thenThrow(new IllegalStateException("base indisponible simulée"));

        var report = worker(reconciliation).runOnce(Instant.now());

        assertEquals(1, report.errors(), "L'échec de lecture du lot est compté, sans propager l'exception");
        assertTrue(isLockFreeFromOtherSession(), "Aucun verrou orphelin après exception");
    }

    @Test
    public void lockIsReleasedAfterUnexpectedError() throws Exception {
        requirePostgres();
        var reconciliation = mock(PaymentReconciliationService.class);
        when(reconciliation.reconcileBatch(any(), anyInt())).thenThrow(new AssertionError("erreur grave simulée"));

        assertThrows(AssertionError.class, () -> worker(reconciliation).runOnce(Instant.now()));

        assertTrue(isLockFreeFromOtherSession(), "Le verrou doit être libéré même si une Error remonte");
    }

    @Test
    public void lockIsFreeAgainAfterReleaseSoNextRunProceeds() throws Exception {
        requirePostgres();
        var reconciliation = mock(PaymentReconciliationService.class);
        when(reconciliation.reconcileBatch(any(), anyInt())).thenReturn(List.of());
        var worker = worker(reconciliation);

        worker.runOnce(Instant.now());
        var second = worker.runOnce(Instant.now());

        assertEquals(0, second.skippedBecauseLocked(), "Une reprise après libération doit être possible");
    }
}
