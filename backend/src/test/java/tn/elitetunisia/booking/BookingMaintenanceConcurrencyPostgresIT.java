package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleService;
import tn.elitetunisia.vehicle.VehicleStatus;
import tn.elitetunisia.vehicle.VehicleStatusRequest;

/**
 * Confirmation d'une réservation simultanée à une mise en maintenance du même véhicule (PostgreSQL réel).
 * Invariant : jamais de réservation CONFIRMED sur un véhicule en MAINTENANCE ; exactement une opération réussit.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class BookingMaintenanceConcurrencyPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final int ROUNDS = 20;

    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired VehicleService vehicleService;
    @Autowired VehicleRepository vehicles;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    @Test
    void confirmationAndMaintenanceNeverCoexist() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var fixture = fixtures.createBooking(24);
            fixtures.payInFull(fixture.booking().id(), fixture.owner());
            UUID bookingId = fixture.booking().id();
            UUID vehicleId = fixture.vehicle();

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> confirm = executor.submit(() -> afterBarrier(ready, go,
                        () -> bookingService.transition(bookingId, BookingStatus.CONFIRMED, fixture.owner(), null)));
                Future<?> maintenance = executor.submit(() -> afterBarrier(ready, go,
                        () -> vehicleService.updateStatus(vehicleId, new VehicleStatusRequest(VehicleStatus.MAINTENANCE), fixture.owner(), null)));
                ready.await();
                go.countDown();

                boolean confirmed = succeeded(confirm, "round " + round);
                boolean maintained = succeeded(maintenance, "round " + round);
                assertTrue(confirmed ^ maintained, "Exactement une opération doit réussir (round " + round + ")");

                BookingStatus bookingStatus = bookings.findById(bookingId).orElseThrow().getStatus();
                VehicleStatus vehicleStatus = vehicles.findById(vehicleId).orElseThrow().getStatus();
                assertFalse(bookingStatus == BookingStatus.CONFIRMED && vehicleStatus == VehicleStatus.MAINTENANCE,
                        "Réservation CONFIRMED sur véhicule en maintenance (round " + round + ")");
                if (confirmed) {
                    assertEquals(BookingStatus.CONFIRMED, bookingStatus);
                    assertEquals(VehicleStatus.AVAILABLE, vehicleStatus);
                } else {
                    assertEquals(VehicleStatus.MAINTENANCE, vehicleStatus);
                    assertEquals(BookingStatus.PENDING, bookingStatus);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static boolean succeeded(Future<?> future, String label) throws Exception {
        try {
            assertNotNull(future.get(30, TimeUnit.SECONDS), label);
            return true;
        } catch (ExecutionException exception) {
            assertTrue(exception.getCause() instanceof RuntimeException, label);
            return false;
        }
    }

    private static <T> T afterBarrier(CountDownLatch ready, CountDownLatch go, Callable<T> action) throws Exception {
        ready.countDown();
        go.await();
        return action.call();
    }
}
