package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * Demandes PENDING incompatibles : coexistence, absence d'expiration automatique et
 * garantie de non-double confirmation sur PostgreSQL réel (deux transactions indépendantes).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
class BookingPendingConcurrencyPostgresIT {
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
    void incompatiblePendingRequestsCoexistAndOnlyOneConfirmationSucceeds() throws Exception {
        var first = fixtures.createBooking(24);
        UUID vehicleId = first.vehicle();
        UUID owner = first.owner();
        var firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var second = bookingService.create(new tn.elitetunisia.booking.BookingRequest(vehicleId,
                firstEntity.getStartDateTime(), firstEntity.getEndDateTime(), "Tunis", "Tunis", null), owner, null);

        assertEquals(2, countPending(vehicleId), "les deux demandes PENDING incompatibles doivent coexister");
        UUID idA = first.booking().id();
        UUID idB = second.id();
        fixtures.payInFull(idA, owner);
        fixtures.payInFull(idB, owner);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (UUID id : List.of(idA, idB)) {
            Callable<String> attempt = () -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    bookingService.transition(id, BookingStatus.CONFIRMED, owner, null);
                    return "OK";
                } catch (BookingException ex) {
                    return ex.getCode();
                }
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

        long confirmed = jdbc.queryForObject(
                "select count(*) from bookings where vehicle_id = ? and status in ('CONFIRMED','IN_PROGRESS')", Long.class, vehicleId);
        assertEquals(1L, confirmed, "intégrité : au plus une réservation bloquante par période");
    }

    private long countPending(UUID vehicleId) {
        return jdbc.queryForObject("select count(*) from bookings where vehicle_id = ? and status = 'PENDING'", Long.class, vehicleId);
    }
}
