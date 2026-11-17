package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.partner.GuaranteeService;

/**
 * Scénarios de concurrence sur PostgreSQL réel (transactions indépendantes, threads synchronisés par barrière).
 * Chaque tour crée ses propres véhicule, garantie, clients et réservations : aucune dépendance aux données résiduelles.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
class BookingConcurrencyScenariosPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final int ROUNDS = 20;

    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired GuaranteeService guaranteeService;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    private ExecutorService executor;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
        executor = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    /** Scénario 2 : périodes disjointes sur le même véhicule : les deux confirmations doivent réussir. */
    @Test
    void disjointPeriodsBothConfirm() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var first = fixtures.createBooking(24);
            var firstEntity = bookings.findById(first.booking().id()).orElseThrow();
            Instant later = firstEntity.getEndDateTime().plus(2, ChronoUnit.DAYS);
            var second = create(first.vehicle(), first.owner(), later, later.plus(24, ChronoUnit.HOURS));
            fixtures.payInFull(first.booking().id(), first.owner());
            fixtures.payInFull(second.id(), first.owner());

            List<String> outcomes = raceConfirm(first.owner(), first.booking().id(), second.id());

            assertEquals(List.of("OK", "OK"), outcomes, "round " + round);
            assertEquals(0, overlappingBlocking(first.vehicle()), "round " + round);
        }
    }

    /** Scénario 3 : chevauchement partiel : une seule confirmation compatible doit réussir. */
    @Test
    void partialOverlapOnlyOneConfirms() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var first = fixtures.createBooking(24);
            var firstEntity = bookings.findById(first.booking().id()).orElseThrow();
            Instant shifted = firstEntity.getStartDateTime().plus(12, ChronoUnit.HOURS);
            var second = create(first.vehicle(), first.owner(), shifted, shifted.plus(24, ChronoUnit.HOURS));
            fixtures.payInFull(first.booking().id(), first.owner());
            fixtures.payInFull(second.id(), first.owner());

            List<String> outcomes = raceConfirm(first.owner(), first.booking().id(), second.id());

            assertEquals(1, count(outcomes, "OK"), "round " + round + " " + outcomes);
            assertEquals(1, count(outcomes, "VEHICLE_NOT_AVAILABLE"), "round " + round + " " + outcomes);
            assertEquals(0, overlappingBlocking(first.vehicle()), "round " + round);
        }
    }

    /** Scénarios 4 et 8 : chevauchement total entre deux demandes, confirmées simultanément. */
    @Test
    void totalOverlapOnlyOneConfirms() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var first = fixtures.createBooking(24);
            var firstEntity = bookings.findById(first.booking().id()).orElseThrow();
            var second = create(first.vehicle(), first.owner(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime());
            fixtures.payInFull(first.booking().id(), first.owner());
            fixtures.payInFull(second.id(), first.owner());

            List<String> outcomes = raceConfirm(first.owner(), first.booking().id(), second.id());

            assertEquals(1, count(outcomes, "OK"), "round " + round + " " + outcomes);
            assertEquals(0, overlappingBlocking(first.vehicle()), "round " + round);
        }
    }

    /** Scénario 5 : retrait de la garantie partenaire pendant une confirmation. Invariant : pas de CONFIRMED sur garantie retirée. */
    @Test
    void guaranteeWithdrawalNeverLeavesConfirmedBookingUncovered() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var fixture = fixtures.createBooking(24);
            UUID bookingId = fixture.booking().id();
            fixtures.payInFull(bookingId, fixture.owner());
            UUID guaranteeId = jdbc.queryForObject("select id from vehicle_guarantees where vehicle_id = ?", UUID.class, fixture.vehicle());

            CountDownLatch go = new CountDownLatch(1);
            Future<?> confirm = executor.submit(() -> afterGo(go, () -> bookingService.transition(bookingId, BookingStatus.CONFIRMED, fixture.owner(), null)));
            Future<?> withdraw = executor.submit(() -> afterGo(go, () -> guaranteeService.withdraw(guaranteeId, fixture.owner(), null)));
            go.countDown();

            boolean confirmed = succeeded(confirm);
            boolean withdrawn = succeeded(withdraw);
            String status = bookings.findById(bookingId).orElseThrow().getStatus().name();
            String guarantee = jdbc.queryForObject("select status from vehicle_guarantees where id = ?", String.class, guaranteeId);

            assertFalse(confirmed && withdrawn && "CONFIRMED".equals(status) && "WITHDRAWN".equals(guarantee),
                    "round " + round + " : CONFIRMED sur garantie retirée");
            assertTrue(confirmed || withdrawn, "round " + round + " : au moins une opération doit réussir");
            if (confirmed) {
                assertEquals("CONFIRMED", status, "round " + round);
            }
            if (withdrawn && !confirmed) {
                assertNotEquals("CONFIRMED", status, "round " + round);
            }
        }
    }

    /** Scénario 6 : rollback du perdant : aucune transition ni événement persistés pour une tentative échouée. */
    @Test
    void losingConfirmationLeavesNoPartialWrite() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var first = fixtures.createBooking(24);
            var firstEntity = bookings.findById(first.booking().id()).orElseThrow();
            var second = create(first.vehicle(), first.owner(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime());
            fixtures.payInFull(first.booking().id(), first.owner());
            fixtures.payInFull(second.id(), first.owner());

            List<String> outcomes = raceConfirm(first.owner(), first.booking().id(), second.id());

            UUID winner = outcomes.get(0).equals("OK") ? first.booking().id() : second.id();
            UUID loser = winner.equals(first.booking().id()) ? second.id() : first.booking().id();
            assertEquals("CONFIRMED", bookings.findById(winner).orElseThrow().getStatus().name(), "round " + round);
            assertEquals("PENDING", bookings.findById(loser).orElseThrow().getStatus().name(), "round " + round);
            assertEquals(0, eventCount(loser, "BOOKING_CONFIRMED"), "round " + round + " : événement fantôme sur le perdant");
            assertEquals(1, eventCount(winner, "BOOKING_CONFIRMED"), "round " + round);
        }
    }

    /** Scénario 7 : confirmation et annulation simultanées de la même réservation : un seul état final cohérent. */
    @Test
    void concurrentConfirmAndCancelReachOneConsistentState() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var fixture = fixtures.createBooking(24);
            UUID bookingId = fixture.booking().id();
            fixtures.payInFull(bookingId, fixture.owner());

            CountDownLatch go = new CountDownLatch(1);
            Future<?> confirm = executor.submit(() -> afterGo(go, () -> bookingService.transition(bookingId, BookingStatus.CONFIRMED, fixture.owner(), null)));
            Future<?> cancel = executor.submit(() -> afterGo(go, () -> bookingService.customerCancel(bookingId, fixture.owner(), null)));
            go.countDown();
            succeeded(confirm);
            succeeded(cancel);

            String status = bookings.findById(bookingId).orElseThrow().getStatus().name();
            assertTrue(List.of("CONFIRMED", "CANCELLED").contains(status), "round " + round + " : état final " + status);
            int confirmedEvents = eventCount(bookingId, "BOOKING_CONFIRMED");
            int cancelledEvents = eventCount(bookingId, "BOOKING_CANCELLED");
            assertTrue(confirmedEvents <= 1 && cancelledEvents <= 1, "round " + round + " : événement dupliqué");
            assertEquals("CANCELLED".equals(status) ? 1 : 0, cancelledEvents, "round " + round);
        }
    }

    /** Scénario 8 : confirmations simultanées répétées de la même réservation : idempotence, un seul événement. */
    @Test
    void repeatedConcurrentConfirmationIsIdempotent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var fixture = fixtures.createBooking(24);
            UUID bookingId = fixture.booking().id();
            fixtures.payInFull(bookingId, fixture.owner());

            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> attempts = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                attempts.add(executor.submit(() -> afterGo(go, () -> bookingService.transition(bookingId, BookingStatus.CONFIRMED, fixture.owner(), null))));
            }
            go.countDown();
            for (Future<?> attempt : attempts) {
                assertNotNull(attempt.get(30, TimeUnit.SECONDS), "round " + round);
            }

            assertEquals("CONFIRMED", bookings.findById(bookingId).orElseThrow().getStatus().name(), "round " + round);
            assertEquals(1, eventCount(bookingId, "BOOKING_CONFIRMED"), "round " + round + " : confirmation dupliquée");
        }
    }

    /** Scénario 9 : après concurrence, aucun chevauchement bloquant ni référence orpheline. */
    @Test
    void integrityHoldsAfterConcurrentRounds() {
        Integer orphans = jdbc.queryForObject("select count(*) from booking_events e "
                + "where not exists (select 1 from bookings b where b.id = e.booking_id)", Integer.class);
        assertEquals(0, orphans, "événements sans réservation");
        Integer overlaps = jdbc.queryForObject("select count(*) from bookings a join bookings b "
                + "on a.vehicle_id = b.vehicle_id and a.id < b.id "
                + "and a.status in ('CONFIRMED','IN_PROGRESS') and b.status in ('CONFIRMED','IN_PROGRESS') "
                + "and a.starts_at < b.ends_at and b.starts_at < a.ends_at", Integer.class);
        assertEquals(0, overlaps, "chevauchements bloquants persistés");
    }

    private BookingSummary create(UUID vehicleId, UUID owner, Instant start, Instant end) {
        return bookingService.create(new BookingRequest(vehicleId, start, end, "Tunis", "Tunis", null), owner, null);
    }

    /** Lance deux confirmations simultanées ; retourne "OK" ou le code métier pour chacune, dans l'ordre des identifiants. */
    private List<String> raceConfirm(UUID owner, UUID idA, UUID idB) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (UUID id : List.of(idA, idB)) {
            results.add(executor.submit(() -> {
                go.await(10, TimeUnit.SECONDS);
                try {
                    bookingService.transition(id, BookingStatus.CONFIRMED, owner, null);
                    return "OK";
                } catch (BookingException ex) {
                    return ex.getCode();
                }
            }));
        }
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private static <T> T afterGo(CountDownLatch go, Callable<T> action) throws Exception {
        go.await(10, TimeUnit.SECONDS);
        return action.call();
    }

    private static boolean succeeded(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.ExecutionException exception) {
            if (exception.getCause() instanceof BookingException || exception.getCause() instanceof DomainException) {
                return false;
            }
            throw exception;
        }
    }

    private static long count(List<String> outcomes, String code) {
        return outcomes.stream().filter(code::equals).count();
    }

    private int overlappingBlocking(UUID vehicleId) {
        return jdbc.queryForObject("select count(*) from bookings a join bookings b on a.id < b.id "
                + "and a.vehicle_id = ? and b.vehicle_id = ? "
                + "and a.status in ('CONFIRMED','IN_PROGRESS') and b.status in ('CONFIRMED','IN_PROGRESS') "
                + "and a.starts_at < b.ends_at and b.starts_at < a.ends_at", Integer.class, vehicleId, vehicleId);
    }

    private int eventCount(UUID bookingId, String action) {
        return jdbc.queryForObject("select count(*) from booking_events where booking_id = ? and action = ?", Integer.class, bookingId, action);
    }
}
