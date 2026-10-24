package tn.elitetunisia.driver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class DriverAssignmentPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired DriverAssignmentService service;
    @Autowired BookingDriverAssignmentRepository assignments;
    @Autowired DriverRepository drivers;
    @Autowired TestFixtures fixtures;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    @Test
    public void concurrentAssignmentsAllowExactlyOneOverlappingBooking() throws Exception {
        var first = fixtures.createBooking(24);
        var second = fixtures.createBooking(24);
        var driver = new DriverEntity("Integration", "Driver", "+21600000000");
        driver.setLicenseNumber("test-" + UUID.randomUUID());
        var savedDriver = drivers.saveAndFlush(driver);
        UUID driverId = savedDriver.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstAttempt = executor.submit(() -> assignAfterBarrier(
                    ready, start, first.booking().id(), driverId));
            Future<Boolean> secondAttempt = executor.submit(() -> assignAfterBarrier(
                    ready, start, second.booking().id(), driverId));

            ready.await();
            start.countDown();

            assertNotEquals(firstAttempt.get(), secondAttempt.get(),
                    "Exactly one concurrent assignment should succeed.");
            assertEquals(1, (assignments.findByBookingId(first.booking().id()) != null ? 1 : 0)
                    + (assignments.findByBookingId(second.booking().id()) != null ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean assignAfterBarrier(CountDownLatch ready, CountDownLatch start, UUID bookingId, UUID driverId)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.assignDriver(bookingId, driverId);
            return true;
        } catch (RuntimeException exception) {
            if ("DRIVER_NOT_AVAILABLE".equals(exception.getMessage())) return false;
            throw exception;
        }
    }
}
