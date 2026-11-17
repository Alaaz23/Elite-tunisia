package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
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

/**
 * Règles actuelles des réservations sur PostgreSQL réel : les PENDING ne bloquent jamais, seules
 * CONFIRMED et IN_PROGRESS bloquent, la période est semi-ouverte [début, fin[, et la vérification
 * définitive a lieu à la confirmation. Aucune expiration automatique des PENDING n'est testée.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
class BookingHoldPostgresTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final String NOT_AVAILABLE = "VEHICLE_NOT_AVAILABLE";

    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    private ExecutorService executor;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void multiplePendingRequestsCoexistOnTheSamePeriod() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        bookingService.create(request(first.vehicle(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime()),
                first.owner(), null);

        assertEquals(2, countWithStatus(first.vehicle(), "PENDING"), "deux PENDING sur la même période doivent coexister");
    }

    @Test
    void pendingRequestsDoNotBlockAvailability() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var second = bookingService.create(
                request(first.vehicle(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime()), first.owner(), null);

        assertEquals(BookingStatus.PENDING, second.status(), "la création d'une PENDING incompatible ne doit pas être refusée");
        assertEquals(0, countBlocking(first.vehicle()), "une PENDING ne compte pas comme bloquante");
    }

    @Test
    void confirmedBookingBlocksIncompatiblePeriod() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var second = bookingService.create(
                request(first.vehicle(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime()), first.owner(), null);

        fixtures.payInFull(first.booking().id(), first.owner());
        bookingService.transition(first.booking().id(), BookingStatus.CONFIRMED, first.owner(), null);

        assertEquals(NOT_AVAILABLE, confirmInTransaction(second.id(), first.owner()));
    }

    @Test
    void inProgressBookingBlocksIncompatiblePeriod() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var second = bookingService.create(
                request(first.vehicle(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime()), first.owner(), null);

        jdbc.update("update bookings set status = 'IN_PROGRESS' where id = ?", first.booking().id());

        assertEquals(NOT_AVAILABLE, confirmInTransaction(second.id(), first.owner()));
    }

    @Test
    void adjacentHalfOpenPeriodsAreBothConfirmable() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var adjacent = bookingService.create(request(first.vehicle(), firstEntity.getEndDateTime(),
                firstEntity.getEndDateTime().plus(Duration.ofHours(24))), first.owner(), null);

        fixtures.payInFull(first.booking().id(), first.owner());
        fixtures.payInFull(adjacent.id(), first.owner());
        bookingService.transition(first.booking().id(), BookingStatus.CONFIRMED, first.owner(), null);
        bookingService.transition(adjacent.id(), BookingStatus.CONFIRMED, first.owner(), null);

        assertEquals(2, countWithStatus(first.vehicle(), "CONFIRMED"), "[début, fin[ : deux périodes adjacentes sont compatibles");
    }

    @Test
    void concurrentConfirmationsNeverBothSucceed() throws Exception {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var second = bookingService.create(
                request(first.vehicle(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime()), first.owner(), null);
        UUID owner = first.owner();
        fixtures.payInFull(first.booking().id(), owner);
        fixtures.payInFull(second.id(), owner);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (UUID id : List.of(first.booking().id(), second.id())) {
            Callable<String> attempt = () -> {
                start.await(10, TimeUnit.SECONDS);
                return confirmInTransaction(id, owner);
            };
            results.add(executor.submit(attempt));
        }
        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        assertEquals(1, outcomes.stream().filter("OK"::equals).count(), "une seule confirmation doit réussir : " + outcomes);
        assertEquals(1, outcomes.stream().filter(NOT_AVAILABLE::equals).count(), "le perdant doit recevoir VEHICLE_NOT_AVAILABLE : " + outcomes);
        assertEquals(1, countWithStatus(first.vehicle(), "CONFIRMED"), "intégrité : une seule réservation CONFIRMED");
    }

    @Test
    void rejectedConfirmationLeavesNoInconsistentData() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var second = bookingService.create(
                request(first.vehicle(), firstEntity.getStartDateTime(), firstEntity.getEndDateTime()), first.owner(), null);
        fixtures.payInFull(first.booking().id(), first.owner());
        fixtures.payInFull(second.id(), first.owner());
        bookingService.transition(first.booking().id(), BookingStatus.CONFIRMED, first.owner(), null);
        long bookingsBefore = countAll(first.vehicle());

        assertEquals(NOT_AVAILABLE, confirmInTransaction(second.id(), first.owner()));

        assertEquals(bookingsBefore, countAll(first.vehicle()), "le rejet ne doit ni créer ni supprimer de réservation");
        assertEquals("PENDING", statusOf(second.id()), "la réservation refusée doit rester PENDING");
        assertEquals("CONFIRMED", statusOf(first.booking().id()), "la confirmation valide ne doit pas être annulée");
        assertEquals(1, countWithStatus(first.vehicle(), "CONFIRMED"), "aucun chevauchement CONFIRMED après conflit");
    }

    private static BookingRequest request(UUID vehicleId, Instant start, Instant end) {
        return new BookingRequest(vehicleId, start, end, "Tunis", "Tunis", null);
    }

    /** Tente la confirmation dans sa propre transaction et renvoie "OK" ou le code métier refusé. */
    private String confirmInTransaction(UUID bookingId, UUID owner) {
        try {
            bookingService.transition(bookingId, BookingStatus.CONFIRMED, owner, null);
            return "OK";
        } catch (BookingException ex) {
            return ex.getCode();
        }
    }

    private long countWithStatus(UUID vehicleId, String status) {
        return jdbc.queryForObject("select count(*) from bookings where vehicle_id = ? and status = ?", Long.class, vehicleId, status);
    }

    private long countBlocking(UUID vehicleId) {
        return jdbc.queryForObject(
                "select count(*) from bookings where vehicle_id = ? and status in ('CONFIRMED','IN_PROGRESS')", Long.class, vehicleId);
    }

    private long countAll(UUID vehicleId) {
        return jdbc.queryForObject("select count(*) from bookings where vehicle_id = ?", Long.class, vehicleId);
    }

    private String statusOf(UUID bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }
}
