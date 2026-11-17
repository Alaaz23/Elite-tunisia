package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import tn.elitetunisia.vehicle.VehicleStatus;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class BookingLockOrderPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final int ROUNDS = 100;

    @Autowired BookingService bookingService;
    @Autowired BookingOperationService operations;
    @Autowired BookingRepository bookings;
    @Autowired BookingOperationRepository ops;
    @Autowired VehicleRepository vehicles;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    // Chaque appel crée un véhicule et un client neufs : aucune dépendance aux données résiduelles.
    private TestFixtures.Fixture confirmed() {
        var fixture = fixtures.createBooking(24);
        fixtures.payInFull(fixture.booking().id(), fixture.owner());
        bookingService.transition(fixture.booking().id(), BookingStatus.CONFIRMED, fixture.owner(), null);
        return fixture;
    }

    @Test
    void pickupWaitsOnVehicleBeforeAcquiringBooking() throws Exception {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        try (Connection holder = jdbc.getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            lockRow(holder, "vehicles", fixture.vehicle(), false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<BookingOperationEntity> pickup = executor.submit(
                        () -> operations.recordPickup(bookingId, fixture.owner(), 1000, 80));
                awaitBlockedOnLock();
                // Ordre Vehicle → Booking : le pickup ne doit pas encore détenir le verrou Booking.
                try (Connection probe = jdbc.getDataSource().getConnection()) {
                    probe.setAutoCommit(false);
                    assertDoesNotThrow(() -> lockRow(probe, "bookings", bookingId, true));
                    probe.rollback();
                }
                assertFalse(pickup.isDone(), "Le pickup doit attendre le verrou véhicule.");
                holder.rollback();
                assertNotNull(pickup.get(30, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void concurrentPickupAndUpdateNeverDeadlock() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            final int index = round;
            var fixture = confirmed();
            UUID bookingId = fixture.booking().id();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> pickup = executor.submit(() -> afterBarrier(ready, start,
                        () -> operations.recordPickup(bookingId, fixture.owner(), 1000, 80)));
                Future<?> update = executor.submit(() -> afterBarrier(ready, start,
                        () -> bookingService.update(bookingId,
                                new BookingUpdateRequest("Tunis", "Sfax", "round " + index), fixture.owner(), null)));
                ready.await();
                start.countDown();
                assertNotNull(pickup.get(30, TimeUnit.SECONDS), "round " + round);
                try {
                    update.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException exception) {
                    // Update après pickup : rejet métier attendu, pas un deadlock.
                    assertInstanceOf(BookingException.class, exception.getCause(), "round " + round);
                    assertEquals("INVALID_STATUS_TRANSITION", ((BookingException) exception.getCause()).getCode());
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void returnAndNewBookingOnSameVehicleStaySerialized() throws Exception {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        operations.recordPickup(bookingId, fixture.owner(), 1000, 80);
        Instant start = fixture.booking().endDateTime().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(24, ChronoUnit.HOURS);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> returned = executor.submit(() -> afterBarrier(ready, go,
                    () -> operations.recordReturn(bookingId, fixture.owner(), 1200, 60, null, null)));
            Future<?> created = executor.submit(() -> afterBarrier(ready, go,
                    () -> bookingService.create(new BookingRequest(fixture.vehicle(), start, end, "Tunis", "Tunis", null),
                            fixture.owner(), null)));
            ready.await();
            go.countDown();
            assertNotNull(returned.get(30, TimeUnit.SECONDS));
            try {
                created.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                // Création exécutée avant le retour : véhicule encore loué, rejet attendu.
                // Création exécutée après le retour : elle peut réussir, sans chevauchement de dates.
                assertInstanceOf(BookingException.class, exception.getCause());
                assertEquals("VEHICLE_NOT_AVAILABLE", ((BookingException) exception.getCause()).getCode());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(BookingStatus.COMPLETED, bookings.findById(bookingId).orElseThrow().getStatus());
        assertEquals(VehicleStatus.AVAILABLE, vehicles.findById(fixture.vehicle()).orElseThrow().getStatus());
        assertEquals(1, countOperations(bookingId, "RETURN"));
    }

    @Test
    void concurrentPickupsCreateExactlyOneOperation() throws Exception {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<BookingOperationEntity> first = executor.submit(() -> afterBarrier(ready, go,
                    () -> operations.recordPickup(bookingId, fixture.owner(), 1000, 80)));
            Future<BookingOperationEntity> second = executor.submit(() -> afterBarrier(ready, go,
                    () -> operations.recordPickup(bookingId, fixture.owner(), 1000, 80)));
            ready.await();
            go.countDown();
            // Le verrou sérialise les pickups : le second voit le PICKUP déjà commité et le renvoie (idempotence validée).
            UUID firstId = first.get(30, TimeUnit.SECONDS).getId();
            UUID secondId = second.get(30, TimeUnit.SECONDS).getId();
            assertEquals(firstId, secondId);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, countOperations(bookingId, "PICKUP"));
    }

    @Test
    void rejectedPickupLeavesNoPartialWrite() {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        var ex = assertThrows(BookingException.class, () -> operations.recordPickup(bookingId, fixture.owner(), -5, 80));
        assertEquals("INVALID_DATA", ex.getCode());
        assertEquals(BookingStatus.CONFIRMED, bookings.findById(bookingId).orElseThrow().getStatus());
        assertEquals(VehicleStatus.AVAILABLE, vehicles.findById(fixture.vehicle()).orElseThrow().getStatus());
        assertTrue(ops.findByBookingIdOrderByCreatedAtAsc(bookingId).isEmpty());
    }

    @Test
    void overlappingBookingStaysBlockedWhileConfirmed() {
        var fixture = confirmed();
        var booking = fixture.booking();
        var ex = assertThrows(BookingException.class, () -> bookingService.create(
                new BookingRequest(fixture.vehicle(), booking.startDateTime(), booking.endDateTime(), "Tunis", "Tunis", null),
                fixture.owner(), null));
        assertEquals("VEHICLE_NOT_AVAILABLE", ex.getCode());
    }

    private int countOperations(UUID bookingId, String action) {
        return (int) ops.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .filter(op -> action.equals(op.getAction())).count();
    }

    private static <T> T afterBarrier(CountDownLatch ready, CountDownLatch go, Callable<T> action) throws Exception {
        ready.countDown();
        go.await();
        return action.call();
    }

    private static void lockRow(Connection connection, String table, UUID id, boolean noWait) throws SQLException {
        String sql = "SELECT id FROM " + table + " WHERE id = ? FOR UPDATE" + (noWait ? " NOWAIT" : "");
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.executeQuery().close();
        }
    }

    private void awaitBlockedOnLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
                    Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(50);
        }
        fail("Le pickup n'a pas atteint l'attente de verrou dans le délai imparti.");
    }
}
