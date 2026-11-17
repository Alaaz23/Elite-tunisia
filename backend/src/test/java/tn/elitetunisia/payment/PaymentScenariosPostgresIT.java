package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.payment.ControlledPaymentProvider.Behavior;
import tn.elitetunisia.payment.ControlledPaymentProvider.SimulatedCrash;
import tn.elitetunisia.payment.provider.PaymentProvider;
import tn.elitetunisia.payment.provider.PaymentResult;
import tn.elitetunisia.quote.QuoteRepository;
import tn.elitetunisia.quote.QuoteRequest;
import tn.elitetunisia.quote.QuoteService;

/**
 * Neuf scénarios de paiement sur PostgreSQL réel (elite_a4_test) avec un fournisseur de test contrôlé.
 * Aucun fournisseur réel n'est utilisé. Le workflow financier n'est pas modifié.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
@Tag("postgres")
public class PaymentScenariosPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final String EXPECTED_DB = "elite_a4_test";

    @Autowired PaymentRepository payments;
    @Autowired PaymentEventRepository events;
    @Autowired QuoteRepository quoteRepository;
    @Autowired BookingService bookingService;
    @Autowired TransactionTemplate tx;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quotes;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
        String db = jdbc.queryForObject("SELECT current_database()", String.class);
        assertEquals(EXPECTED_DB, db, "Les tests de paiement doivent cibler uniquement elite_a4_test");
    }

    private record Accepted(UUID quoteId, UUID owner, BigDecimal total) {}

    private Accepted acceptedQuote() {
        var fixture = fixtures.createBooking(24);
        var request = new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(ChronoUnit.MICROS), false,
                BigDecimal.ZERO, "", "", List.of());
        var quote = quotes.create(fixture.booking().id(), request, fixture.owner(), null);
        quotes.send(quote.id(), fixture.owner(), null);
        quotes.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        return new Accepted(quote.id(), fixture.owner(), quote.totalAmount());
    }

    private PaymentService service(ControlledPaymentProvider provider, PaymentEventRepository eventRepo) {
        return new PaymentService(payments, eventRepo, provider, quoteRepository, bookingService, tx);
    }

    private PaymentService service(ControlledPaymentProvider provider) {
        return service(provider, events);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String statusOf(UUID quoteId, String key) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE quote_id = ? AND idempotency_key = ?", String.class, quoteId, key);
    }

    private String refOf(UUID quoteId, String key) {
        return jdbc.queryForObject("SELECT transaction_ref FROM payments WHERE quote_id = ? AND idempotency_key = ?", String.class, quoteId, key);
    }

    private long eventCount(UUID quoteId, String action) {
        return count("SELECT count(*) FROM payment_events e JOIN payments p ON e.payment_id = p.id WHERE p.quote_id = ? AND e.action = ?",
                quoteId, action);
    }

    // 10. Ordre de verrouillage : la finalisation attend le verrou véhicule avant d'écrire sur la tentative (Vehicle → Booking → Payment).
    @Test
    public void s10_finishWaitsOnVehicleBeforeLockingPayment() throws Exception {
        var q = acceptedQuote();
        String key = "s10-" + UUID.randomUUID();
        UUID vehicleId = jdbc.queryForObject(
                "SELECT b.vehicle_id FROM bookings b JOIN quotes qt ON qt.booking_id = b.id WHERE qt.id = ?", UUID.class, q.quoteId());
        var providerEntered = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE).beforeProcess(() -> {
            providerEntered.countDown();
            try { releaseProvider.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        var executor = Executors.newSingleThreadExecutor();
        try (var lockHolder = jdbc.getDataSource().getConnection()) {
            lockHolder.setAutoCommit(false);
            Future<PaymentEntity> run = executor.submit(() -> service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
            assertTrue(providerEntered.await(30, TimeUnit.SECONDS), "le fournisseur doit être atteint");
            UUID paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE quote_id = ? AND idempotency_key = ?", UUID.class, q.quoteId(), key);

            // Une autre transaction détient le véhicule : la finalisation doit attendre ce verrou.
            try (var st = lockHolder.prepareStatement("SELECT id FROM vehicles WHERE id = ? FOR UPDATE")) {
                st.setObject(1, vehicleId);
                st.executeQuery().close();
            }
            releaseProvider.countDown();
            Thread.sleep(500);
            assertFalse(run.isDone(), "la finalisation doit attendre le verrou véhicule");

            // La tentative ne doit pas être verrouillée par la finalisation pendant cette attente.
            try (var probe = jdbc.getDataSource().getConnection()) {
                probe.setAutoCommit(false);
                try (var st = probe.prepareStatement("SELECT id FROM payments WHERE id = ? FOR UPDATE NOWAIT")) {
                    st.setObject(1, paymentId);
                    st.executeQuery().close();
                }
                probe.rollback();
            }

            lockHolder.rollback();
            var result = run.get(30, TimeUnit.SECONDS);
            assertEquals(PaymentStatus.CAPTURED, result.getStatus());
            assertEquals("CAPTURED", statusOf(q.quoteId(), key));
            assertEquals(1, provider.captures());
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    // 11. Concurrence finish / réconciliation, réconciliation mise en file en premier. Une seule capture, finish idempotent.
    @Test
    public void s11_reconcileQueuedBeforeFinish_confirmsOnceWithoutDeadlock() throws Exception {
        runFinishReconcileRace(true);
    }

    // 12. Concurrence finish / réconciliation, finish mis en file en premier. La réconciliation voit la capture et ne réapplique rien.
    @Test
    public void s12_finishQueuedBeforeReconcile_reconcileDoesNotReapply() throws Exception {
        runFinishReconcileRace(false);
    }

    // 13. Deux réconciliations simultanées sur une capture non finalisée : une seule confirmation, aucune nouvelle capture.
    @Test
    public void s13_twoConcurrentReconciliations_confirmOnceWithoutDoubleCapture() throws Exception {
        var q = acceptedQuote();
        String key = "rec2-" + UUID.randomUUID();
        UUID vehicleId = jdbc.queryForObject(
                "SELECT b.vehicle_id FROM bookings b JOIN quotes qt ON qt.booking_id = b.id WHERE qt.id = ?", UUID.class, q.quoteId());
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE);
        var providerReturned = new CountDownLatch(1);
        var releaseFinish = new CountDownLatch(1);
        PaymentProvider wrapped = new PaymentProvider() {
            @Override public PaymentResult process(PaymentEntity payment) {
                var result = provider.process(payment);
                providerReturned.countDown();
                try { releaseFinish.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return result;
            }
            @Override public java.util.Optional<PaymentResult> lookup(String idempotencyKey) {
                return provider.lookup(idempotencyKey);
            }
        };
        var finisher = Executors.newSingleThreadExecutor();
        var reconciler = Executors.newFixedThreadPool(2);
        try (var lockHolder = jdbc.getDataSource().getConnection()) {
            lockHolder.setAutoCommit(false);
            Future<PaymentEntity> finish = finisher.submit(() ->
                    new PaymentService(payments, events, wrapped, quoteRepository, bookingService, tx)
                            .createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
            assertTrue(providerReturned.await(30, TimeUnit.SECONDS), "le fournisseur doit avoir capturé");
            UUID paymentId = paymentIdOf(q.quoteId(), key);

            try (var st = lockHolder.prepareStatement("SELECT id FROM vehicles WHERE id = ? FOR UPDATE")) {
                st.setObject(1, vehicleId);
                st.executeQuery().close();
            }
            var service = new PaymentReconciliationService(payments, events, provider, bookingService, tx);
            Future<PaymentReconciliationService.Outcome> first = reconciler.submit(() -> service.reconcile(paymentId));
            awaitLockWaiters(1);
            Future<PaymentReconciliationService.Outcome> second = reconciler.submit(() -> service.reconcile(paymentId));
            awaitLockWaiters(2);

            lockHolder.rollback();
            var a = first.get(30, TimeUnit.SECONDS);
            var b = second.get(30, TimeUnit.SECONDS);
            var details = java.util.List.of(a.detail(), b.detail());
            assertEquals(1L, details.stream().filter("CAPTURED"::equals).count(), "une seule réconciliation confirme : " + details);
            assertEquals(1L, details.stream().filter("NOT_RECONCILABLE"::equals).count(), "la seconde ne réapplique rien : " + details);

            releaseFinish.countDown();
            var finished = finish.get(30, TimeUnit.SECONDS);
            assertEquals(PaymentStatus.CAPTURED, finished.getStatus());
            assertEquals(1, provider.captures(), "une seule capture fournisseur");
            assertEquals(1L, count("SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", q.quoteId()));
            assertEquals(1L, eventCount(q.quoteId(), "RECONCILED_CAPTURED"));
        } finally {
            releaseFinish.countDown();
            finisher.shutdownNow();
            reconciler.shutdownNow();
        }
    }

    /**
     * Les deux traitements attendent le même verrou véhicule (premier ordre global). La file d'attente est
     * observée dans pg_stat_activity, sans délai arbitraire. La ligne de paiement doit rester libre pendant l'attente.
     */
    private void runFinishReconcileRace(boolean reconcileFirst) throws Exception {
        var q = acceptedQuote();
        String key = "race-" + UUID.randomUUID();
        UUID vehicleId = jdbc.queryForObject(
                "SELECT b.vehicle_id FROM bookings b JOIN quotes qt ON qt.booking_id = b.id WHERE qt.id = ?", UUID.class, q.quoteId());
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE);
        var providerReturned = new CountDownLatch(1);
        var releaseFinish = new CountDownLatch(1);
        PaymentProvider wrapped = new PaymentProvider() {
            @Override public PaymentResult process(PaymentEntity payment) {
                var result = provider.process(payment);
                providerReturned.countDown();
                try { releaseFinish.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return result;
            }
            @Override public java.util.Optional<PaymentResult> lookup(String idempotencyKey) {
                return provider.lookup(idempotencyKey);
            }
        };
        var finisher = Executors.newSingleThreadExecutor();
        var reconciler = Executors.newSingleThreadExecutor();
        try (var lockHolder = jdbc.getDataSource().getConnection()) {
            lockHolder.setAutoCommit(false);
            Future<PaymentEntity> finish = finisher.submit(() ->
                    new PaymentService(payments, events, wrapped, quoteRepository, bookingService, tx)
                            .createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
            assertTrue(providerReturned.await(30, TimeUnit.SECONDS), "le fournisseur doit avoir capturé");
            UUID paymentId = paymentIdOf(q.quoteId(), key);

            // Le véhicule est bloqué par une autre transaction : les deux traitements vont se mettre en file.
            try (var st = lockHolder.prepareStatement("SELECT id FROM vehicles WHERE id = ? FOR UPDATE")) {
                st.setObject(1, vehicleId);
                st.executeQuery().close();
            }
            Future<PaymentReconciliationService.Outcome> reconciliation = null;
            if (reconcileFirst) {
                reconciliation = reconciler.submit(() ->
                        new PaymentReconciliationService(payments, events, provider, bookingService, tx).reconcile(paymentId));
                awaitLockWaiters(1);
                releaseFinish.countDown();
                awaitLockWaiters(2);
            } else {
                releaseFinish.countDown();
                awaitLockWaiters(1);
                reconciliation = reconciler.submit(() ->
                        new PaymentReconciliationService(payments, events, provider, bookingService, tx).reconcile(paymentId));
                awaitLockWaiters(2);
            }

            try (var probe = jdbc.getDataSource().getConnection()) {
                probe.setAutoCommit(false);
                try (var st = probe.prepareStatement("SELECT id FROM payments WHERE id = ? FOR UPDATE NOWAIT")) {
                    st.setObject(1, paymentId);
                    st.executeQuery().close();
                }
                probe.rollback();
            }

            lockHolder.rollback();
            var finished = finish.get(30, TimeUnit.SECONDS);
            var outcome = reconciliation.get(30, TimeUnit.SECONDS);

            assertEquals(PaymentStatus.CAPTURED, finished.getStatus());
            assertEquals("CAPTURED", statusOf(q.quoteId(), key));
            assertEquals(provider.captures(), 1, "une seule capture fournisseur");
            assertEquals(1L, count("SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", q.quoteId()));
            assertEquals(finished.getTransactionRef(), refOf(q.quoteId(), key));
            if (reconcileFirst) {
                assertEquals("CAPTURED", outcome.detail());
                assertEquals(1L, eventCount(q.quoteId(), "RECONCILED_CAPTURED"));
                assertEquals(1L, eventCount(q.quoteId(), "FINISH_ALREADY_CONFIRMED"));
            } else {
                assertEquals("NOT_RECONCILABLE", outcome.detail());
                assertEquals(0L, eventCount(q.quoteId(), "RECONCILED_CAPTURED"));
            }
        } finally {
            releaseFinish.countDown();
            finisher.shutdownNow();
            reconciler.shutdownNow();
        }
    }

    private void awaitLockWaiters(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()") < expected) {
            if (System.nanoTime() > deadline) fail("File d'attente de verrou non atteinte : " + expected);
            Thread.sleep(20);
        }
    }

    // 1. PROVIDER_ERROR : erreur sans réponse exploitable. Le paiement passe UNKNOWN (jamais FAILED), aucune capture, aucun second appel.
    @Test
    public void s1_providerError_leavesUnknownWithoutCaptureAndBlocksRetry() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.ERROR);
        String key = "s1-" + UUID.randomUUID();

        var result = service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        assertEquals(PaymentStatus.UNKNOWN, result.getStatus());
        assertEquals(1, provider.calls());
        assertEquals(0, provider.captures());
        assertNull(refOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "PROVIDER_UNKNOWN"));

        var retry = assertThrows(PaymentException.class, () ->
                service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, "s1b-" + UUID.randomUUID()));
        assertEquals("PAYMENT_IN_PROGRESS", retry.getCode());
        assertEquals(1, provider.calls(), "Une nouvelle clé ne doit pas relancer le fournisseur");
    }

    // 2. Timeout fournisseur : délai dépassé avant réponse. Résultat inconnu : UNKNOWN, aucune capture comptée.
    @Test
    public void s2_providerTimeout_leavesUnknownAndNeverCountsCapture() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.TIMEOUT).delayMillis(200);
        String key = "s2-" + UUID.randomUUID();

        var result = service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        assertEquals(PaymentStatus.UNKNOWN, result.getStatus());
        assertEquals(1, provider.calls());
        assertEquals(0, provider.captures());
        assertEquals(0L, count("SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", q.quoteId()));
        assertEquals(1L, eventCount(q.quoteId(), "PROVIDER_UNKNOWN"));
    }

    // 3. Réponse fournisseur perdue après capture : le fournisseur a débité, l'application ne le sait pas.
    //    Le rejeu avec la même clé ne doit jamais rappeler le fournisseur (pas de double débit).
    @Test
    public void s3_lostResponseAfterCapture_replayDoesNotCallProviderAgain() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE_RESPONSE_LOST);
        String key = "s3-" + UUID.randomUUID();

        var first = service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals(PaymentStatus.UNKNOWN, first.getStatus());
        assertEquals(1, provider.captures(), "Le fournisseur a bien capturé");
        assertNull(refOf(q.quoteId(), key), "La réponse étant perdue, la référence n'est pas connue de l'application");

        var replay = service(provider.behavior(Behavior.CAPTURE))
                .createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals(first.getId(), replay.getId());
        assertEquals(PaymentStatus.UNKNOWN, replay.getStatus());
        assertEquals(1, provider.calls(), "Le rejeu ne doit pas relancer une capture");
        assertEquals(1, provider.captures(), "Aucune seconde capture");
        assertEquals(0L, count("SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", q.quoteId()),
                "Aucune capture locale sans réconciliation");
    }

    // 4. Capture réussie suivie d'un échec de finish : la finalisation est annulée, la référence fournisseur
    //    est persistée séparément et le paiement passe UNKNOWN. Aucun second débit avec la même clé.
    @Test
    public void s4_captureThenFinishFailure_persistsReferenceAsUnknownWithoutSecondCapture() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE);
        String key = "s4-" + UUID.randomUUID();
        PaymentEventRepository failingOnProcessed = (PaymentEventRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {PaymentEventRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("saveAndFlush") && args[0] instanceof PaymentEventEntity e
                            && "PROCESSED".equals(e.getAction())) {
                        throw new IllegalStateException("Échec injecté lors de finish");
                    }
                    try {
                        return method.invoke(events, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });

        assertThrows(IllegalStateException.class, () ->
                service(provider, failingOnProcessed).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
        assertEquals(1, provider.captures(), "Le fournisseur a capturé");
        assertEquals("UNKNOWN", statusOf(q.quoteId(), key), "Finalisation annulée : le paiement n'est pas CAPTURED localement");
        String expectedRef = provider.statusOf(key).orElseThrow().transactionRef();
        assertEquals(expectedRef, refOf(q.quoteId(), key), "Référence fournisseur persistée dès qu'elle est connue");
        assertEquals(0L, eventCount(q.quoteId(), "PROCESSED"));
        assertEquals(1L, eventCount(q.quoteId(), "FINISH_FAILED"));

        var replay = service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals(PaymentStatus.UNKNOWN, replay.getStatus());
        assertEquals(1, provider.calls(), "Le rejeu ne doit pas capturer une seconde fois");
    }

    // 5. Rejeu avec la même clé après capture : même paiement, aucun nouvel appel, une seule ligne.
    @Test
    public void s5_replaySameKeyAfterCapture_returnsSamePaymentWithoutNewCapture() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE);
        String key = "s5-" + UUID.randomUUID();

        var first = service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        var replay = service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        assertEquals(PaymentStatus.CAPTURED, first.getStatus());
        assertEquals(first.getId(), replay.getId());
        assertEquals(1, provider.calls());
        assertEquals(1, provider.captures());
        assertEquals(1L, count("SELECT count(*) FROM payments WHERE quote_id = ?", q.quoteId()));
    }

    // 6. Requêtes simultanées, même clé : une seule ligne, un seul appel fournisseur, une seule capture.
    @Test
    public void s6_concurrentSameKey_singleRowSingleProviderCall() throws Exception {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE).delayMillis(300);
        var svc = service(provider);
        String key = "s6-" + UUID.randomUUID();
        runConcurrently(6, i -> svc.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key).getId().toString());

        assertEquals(1L, count("SELECT count(*) FROM payments WHERE quote_id = ? AND idempotency_key = ?", q.quoteId(), key));
        assertEquals(1, provider.calls(), "Un seul appel fournisseur pour une même clé");
        assertEquals(1, provider.captures());
    }

    // 6b. Requêtes simultanées, clés différentes : une seule capture pour le devis.
    @Test
    public void s6b_concurrentDifferentKeys_onlyOneProviderCallAndOneCapture() throws Exception {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE).delayMillis(300);
        var svc = service(provider);
        List<String> outcomes = runConcurrently(4, i -> {
            try {
                return "OK:" + svc.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, "s6b-" + i + "-" + UUID.randomUUID()).getStatus();
            } catch (PaymentException e) {
                return "ERR:" + e.getCode();
            }
        });

        assertEquals(1, provider.calls(), "Une seule tentative peut atteindre le fournisseur. Résultats : " + outcomes);
        assertEquals(1, provider.captures());
        assertTrue(count("SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", q.quoteId()) <= 1);
    }

    // 7. Rejeu avec la même clé et un montant différent : conflit d'idempotence, aucun appel fournisseur.
    @Test
    public void s7_replayWithDifferentAmount_isIdempotencyConflictWithoutProviderCall() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE);
        String key = "s7-" + UUID.randomUUID();
        service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        var ex = assertThrows(PaymentException.class, () ->
                service(provider).createForQuote(q.quoteId(), q.owner(), q.total().add(BigDecimal.ONE), PaymentMethod.CARD, key));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
        assertEquals(1, provider.calls());
        assertEquals(1, provider.captures());
    }

    // 8. Redémarrage pendant une tentative : arrêt du processus pendant l'appel, aucune réponse, aucune capture.
    //    Au redémarrage, le rejeu avec la même clé retrouve la tentative PENDING sans rappeler le fournisseur.
    @Test
    public void s8_restartDuringAttempt_recoversPendingWithoutSecondCapture() {
        var q = acceptedQuote();
        var crashing = new ControlledPaymentProvider().behavior(Behavior.CRASH);
        String key = "s8-" + UUID.randomUUID();

        assertThrows(SimulatedCrash.class, () ->
                service(crashing).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
        assertEquals("PENDING", statusOf(q.quoteId(), key));

        // Nouveau processus : nouvelle instance de service et nouveau fournisseur.
        var afterRestart = new ControlledPaymentProvider().behavior(Behavior.CAPTURE);
        var replay = service(afterRestart).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        assertEquals(PaymentStatus.PENDING, replay.getStatus());
        assertEquals(0, afterRestart.calls(), "Le redémarrage ne doit pas capturer à nouveau sans réconciliation");
        assertEquals(0L, count("SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", q.quoteId()));
    }

    // 9a. Redémarrage après capture : le fournisseur a capturé puis le processus s'arrête avant la réponse.
    //     Après redémarrage, la réconciliation retrouve la capture par clé (PENDING → CAPTURED) sans second appel de capture.
    @Test
    public void s9a_restartAfterCapture_reconciliationRecoversCaptureWithoutSecondCapture() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE_THEN_CRASH);
        String key = "s9a-" + UUID.randomUUID();
        assertThrows(SimulatedCrash.class, () ->
                service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
        assertEquals("PENDING", statusOf(q.quoteId(), key));
        assertNull(refOf(q.quoteId(), key), "Avant réconciliation, la référence n'est pas connue localement");

        // Redémarrage : nouveaux services, même fournisseur (ses preuves existent toujours).
        var outcome = reconciler(provider).reconcile(paymentIdOf(q.quoteId(), key));

        assertEquals(PaymentStatus.CAPTURED, outcome.after());
        assertEquals("CAPTURED", statusOf(q.quoteId(), key));
        assertEquals(provider.statusOf(key).orElseThrow().transactionRef(), refOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "RECONCILED_CAPTURED"));
        assertEquals(1, provider.captures(), "La réconciliation ne doit jamais capturer");
        assertEquals(1, provider.calls());
    }

    // 14. Planificateur : un cycle concurrent détenant le verrou advisory est ignoré, sans consulter ni modifier la tentative.
    @Test
    public void s14_workerSkipsWhenAnotherRunHoldsLock_thenRecoversAfterRelease() throws Exception {
        var q = acceptedQuote();
        String key = "s14-" + UUID.randomUUID();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE_THEN_CRASH);
        assertThrows(SimulatedCrash.class, () ->
                service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));
        var worker = new PaymentReconciliationWorker(jdbc.getDataSource(), reconciler(provider), Duration.ofMinutes(15), 1000);
        var future = Instant.now().plus(1, ChronoUnit.DAYS);

        try (var other = jdbc.getDataSource().getConnection()) {
            setAdvisoryLock(other, "select pg_advisory_lock(?)");
            var skipped = worker.runOnce(future);
            assertEquals(1, skipped.skippedBecauseLocked());
            assertEquals(0, skipped.processed());
            assertEquals("PENDING", statusOf(q.quoteId(), key), "Un cycle bloqué ne doit rien modifier");
            setAdvisoryLock(other, "select pg_advisory_unlock(?)");
        }

        var report = worker.runOnce(future);
        assertEquals(0, report.skippedBecauseLocked());
        assertEquals("CAPTURED", statusOf(q.quoteId(), key));
        assertEquals(1, provider.captures(), "Le planificateur ne doit jamais capturer");
    }

    private static void setAdvisoryLock(java.sql.Connection connection, String sql) throws Exception {
        try (var st = connection.prepareStatement(sql)) {
            st.setLong(1, PaymentReconciliationWorker.LOCK_KEY);
            st.execute();
        }
    }

    // 15. Planificateur : une capture prouvée est récupérée, une tentative sans preuve reste UNKNOWN, et un second cycle ne refait rien.
    @Test
    public void s15_workerRecoversProvenCaptureKeepsUncertainUnknown_andIsIdempotent() throws Exception {
        var captured = acceptedQuote();
        var uncertain = acceptedQuote();
        String capturedKey = "s15a-" + UUID.randomUUID();
        String uncertainKey = "s15b-" + UUID.randomUUID();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE_THEN_CRASH);
        assertThrows(SimulatedCrash.class, () ->
                service(provider).createForQuote(captured.quoteId(), captured.owner(), captured.total(), PaymentMethod.CARD, capturedKey));
        provider.behavior(Behavior.TIMEOUT);
        service(provider).createForQuote(uncertain.quoteId(), uncertain.owner(), uncertain.total(), PaymentMethod.CARD, uncertainKey);
        var worker = new PaymentReconciliationWorker(jdbc.getDataSource(), reconciler(provider), Duration.ofMinutes(15), 1000);
        var future = Instant.now().plus(1, ChronoUnit.DAYS);

        var first = worker.runOnce(future);
        assertEquals(0, first.errors());
        assertEquals("CAPTURED", statusOf(captured.quoteId(), capturedKey));
        assertEquals("UNKNOWN", statusOf(uncertain.quoteId(), uncertainKey));
        assertEquals(1, provider.captures(), "Aucune capture supplémentaire par le planificateur");
        assertEquals(2, provider.calls(), "Seuls les deux appels initiaux au fournisseur sont effectués");

        worker.runOnce(future);
        assertEquals("CAPTURED", statusOf(captured.quoteId(), capturedKey));
        assertEquals("UNKNOWN", statusOf(uncertain.quoteId(), uncertainKey));
        assertEquals(1L, eventCount(captured.quoteId(), "RECONCILED_CAPTURED"));
        assertEquals(1, provider.captures());
    }

    // 9b. PENDING → UNKNOWN : arrêt avant toute réponse, aucune preuve fournisseur. Le résultat reste incertain.
    @Test
    public void s9b_pendingWithoutProof_reconciliationMovesToUnknown() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CRASH);
        String key = "s9b-" + UUID.randomUUID();
        assertThrows(SimulatedCrash.class, () ->
                service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key));

        var outcome = reconciler(provider).reconcile(paymentIdOf(q.quoteId(), key));

        assertEquals(PaymentStatus.PENDING, outcome.before());
        assertEquals(PaymentStatus.UNKNOWN, outcome.after());
        assertEquals("UNKNOWN", statusOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "RECONCILE_UNKNOWN"));
        assertEquals(0, provider.captures());
    }

    // 9c. UNKNOWN → CAPTURED : réponse perdue après capture, la consultation par clé apporte la preuve.
    @Test
    public void s9c_unknownWithCaptureProof_reconciliationMovesToCaptured() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE_RESPONSE_LOST);
        String key = "s9c-" + UUID.randomUUID();
        service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals("UNKNOWN", statusOf(q.quoteId(), key));

        var outcome = reconciler(provider).reconcile(paymentIdOf(q.quoteId(), key));

        assertEquals(PaymentStatus.CAPTURED, outcome.after());
        assertEquals("CAPTURED", statusOf(q.quoteId(), key));
        assertEquals(provider.statusOf(key).orElseThrow().transactionRef(), refOf(q.quoteId(), key));
        assertEquals(1, provider.captures());

        var replay = service(provider.behavior(Behavior.CAPTURE))
                .createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals(PaymentStatus.CAPTURED, replay.getStatus());
        assertEquals(1, provider.calls(), "Après réconciliation, le rejeu ne rappelle pas le fournisseur");
    }

    // 9d. UNKNOWN → FAILED : délai dépassé, la consultation par clé prouve un refus.
    @Test
    public void s9d_unknownWithFailureProof_reconciliationMovesToFailed() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.TIMEOUT);
        String key = "s9d-" + UUID.randomUUID();
        service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        provider.seedLedger(key, new PaymentResult(PaymentStatus.FAILED, null, "refused"));

        var outcome = reconciler(provider).reconcile(paymentIdOf(q.quoteId(), key));

        assertEquals(PaymentStatus.FAILED, outcome.after());
        assertEquals("FAILED", statusOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "RECONCILED_FAILED"));
        assertEquals(0, provider.captures());
    }

    // 9e. Résultat toujours incertain : UNKNOWN sans preuve reste UNKNOWN, et bloque toute nouvelle tentative.
    @Test
    public void s9e_stillUncertain_staysUnknownAndBlocksNewAttempt() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.TIMEOUT);
        String key = "s9e-" + UUID.randomUUID();
        service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        var outcome = reconciler(provider).reconcile(paymentIdOf(q.quoteId(), key));
        assertEquals(PaymentStatus.UNKNOWN, outcome.after());
        assertEquals("UNKNOWN", statusOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "RECONCILE_INCONCLUSIVE"));

        var blocked = assertThrows(PaymentException.class, () ->
                service(provider.behavior(Behavior.CAPTURE)).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, "s9e-b-" + UUID.randomUUID()));
        assertEquals("PAYMENT_IN_PROGRESS", blocked.getCode());
        assertEquals(1, provider.calls(), "Aucun second débit sur un devis encore incertain");
        assertEquals(0, provider.captures());
    }

    // 9f. Réconciliations simultanées : une seule transition vers CAPTURED, un seul événement de confirmation.
    @Test
    public void s9f_concurrentReconciliation_singleConfirmation() throws Exception {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.CAPTURE_RESPONSE_LOST);
        String key = "s9f-" + UUID.randomUUID();
        service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        UUID paymentId = paymentIdOf(q.quoteId(), key);
        var reconciler = reconciler(provider);

        List<String> outcomes = runConcurrently(4, i -> reconciler.reconcile(paymentId).detail());

        assertEquals("CAPTURED", statusOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "RECONCILED_CAPTURED"), "Une seule confirmation. Résultats : " + outcomes);
        assertEquals(1, provider.captures(), "Aucune capture pendant la réconciliation");
    }

    // 9g. Intervention administrative : UNKNOWN → MANUAL_REVIEW, tracée et bloquante.
    @Test
    public void s9g_adminManualReview_isTracedAndBlocksQuote() {
        var q = acceptedQuote();
        var provider = new ControlledPaymentProvider().behavior(Behavior.TIMEOUT);
        String key = "s9g-" + UUID.randomUUID();
        service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        reconciler(provider).requireManualReview(paymentIdOf(q.quoteId(), key), "admin-test", "résultat introuvable");

        assertEquals("MANUAL_REVIEW", statusOf(q.quoteId(), key));
        assertEquals(1L, eventCount(q.quoteId(), "MANUAL_REVIEW"));
        var blocked = assertThrows(PaymentException.class, () ->
                service(provider).createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, "s9g-b-" + UUID.randomUUID()));
        assertEquals("PAYMENT_IN_PROGRESS", blocked.getCode());
    }

    private PaymentReconciliationService reconciler(ControlledPaymentProvider provider) {
        return new PaymentReconciliationService(payments, events, provider, bookingService, tx);
    }

    private UUID paymentIdOf(UUID quoteId, String key) {
        return jdbc.queryForObject("SELECT id FROM payments WHERE quote_id = ? AND idempotency_key = ?", UUID.class, quoteId, key);
    }

    private interface Task { String run(int index) throws Exception; }

    private List<String> runConcurrently(int threads, Task task) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(ex.submit(() -> {
                    start.await();
                    try {
                        return task.run(idx);
                    } catch (PaymentException e) {
                        return "ERR:" + e.getCode();
                    }
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : futures) outcomes.add(f.get(60, TimeUnit.SECONDS));
            Set<String> distinct = new HashSet<>(outcomes);
            assertFalse(distinct.isEmpty());
            return outcomes;
        } finally {
            ex.shutdownNow();
        }
    }
}
