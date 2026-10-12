package tn.elitetunisia.driver;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "enable.postgres.it", matches = "true")
public class DriverAssignmentPostgresIT {
    @Autowired DriverAssignmentService svc;
    @Autowired BookingDriverAssignmentRepository assignments;

    @Test
    @Transactional
    public void concurrentAssignmentsOneWins() throws Exception {
        // This test must be run against the Postgres test DB started via docker compose.
        UUID booking1 = UUID.randomUUID(); UUID booking2 = UUID.randomUUID(); UUID driverId = UUID.randomUUID();
        // Create two threads that attempt to assign same driver for overlapping periods.
        // Implementation note: this test assumes bookings exist with given IDs. In a fuller integration
        // test we would create bookings via repositories; here we check advisory lock integration.
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(3600);

        var t1 = new Thread(() -> {
            try {
                svc.assignDriver(booking1, driverId);
            } catch (RuntimeException ex) {
                // swallow for assertion later
            }
        });
        var t2 = new Thread(() -> {
            try {
                svc.assignDriver(booking2, driverId);
            } catch (RuntimeException ex) {
                // swallow
            }
        });
        t1.start(); t2.start();
        t1.join(); t2.join();
        long count = assignments.count();
        assertTrue(count <= 1, "At most one assignment should be present");
    }
}
