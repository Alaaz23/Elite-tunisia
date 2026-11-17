package tn.elitetunisia.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleStatus;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class BookingAuditAtomicityPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired BookingService bookingService;
    @Autowired BookingOperationService operations;
    @Autowired BookingRepository bookings;
    @Autowired BookingOperationRepository ops;
    @Autowired VehicleRepository vehicles;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean AuditService audit;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    // Chaque test crée ses propres données : aucune dépendance aux données résiduelles.
    private TestFixtures.Fixture confirmed() {
        var fixture = fixtures.createBooking(24);
        fixtures.payInFull(fixture.booking().id(), fixture.owner());
        bookingService.transition(fixture.booking().id(), BookingStatus.CONFIRMED, fixture.owner(), null);
        return fixture;
    }

    // Le stub passe par le proxy transactionnel de recordAudit (MANDATORY) : il doit donc s'exécuter dans une transaction.
    private void failAudit(String action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                doThrow(new IllegalStateException("audit indisponible (test)"))
                        .when(audit).recordAudit(any(), eq(action), any(), any(), any(), any()));
    }

    private long auditCount(UUID entityId, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where entity_id = ? and action = ?", Long.class, entityId, action);
    }

    private long eventCount(UUID bookingId, String action) {
        return jdbc.queryForObject("select count(*) from booking_events where booking_id = ? and action = ?", Long.class, bookingId, action);
    }

    private BookingStatus bookingStatus(UUID bookingId) {
        return bookings.findById(bookingId).orElseThrow().getStatus();
    }

    private VehicleStatus vehicleStatus(UUID vehicleId) {
        return vehicles.findById(vehicleId).orElseThrow().getStatus();
    }

    @Test
    void pickupSuccessRecordsExactlyOneAuditEntry() {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();

        var op = operations.recordPickup(bookingId, fixture.owner(), 1000, 80);

        assertEquals(BookingStatus.IN_PROGRESS, bookingStatus(bookingId));
        assertEquals(VehicleStatus.RENTED, vehicleStatus(fixture.vehicle()));
        assertEquals(1, auditCount(bookingId, "BOOKING_PICKUP"));
        assertEquals(1, eventCount(bookingId, "BOOKING_PICKUP"));
        assertEquals("PICKUP", op.getAction());
    }

    @Test
    void pickupAuditFailurePropagatesAndRollsBackOperation() {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        VehicleStatus vehicleBefore = vehicleStatus(fixture.vehicle());
        failAudit("BOOKING_PICKUP");

        // L'exception d'origine doit remonter : ni avalée, ni transformée en UnexpectedRollbackException.
        assertThrows(IllegalStateException.class, () -> operations.recordPickup(bookingId, fixture.owner(), 1000, 80));

        assertEquals(BookingStatus.CONFIRMED, bookingStatus(bookingId));
        assertEquals(vehicleBefore, vehicleStatus(fixture.vehicle()));
        assertTrue(ops.findByBookingIdOrderByCreatedAtAsc(bookingId).isEmpty());
        assertEquals(0, eventCount(bookingId, "BOOKING_PICKUP"));
        assertEquals(0, auditCount(bookingId, "BOOKING_PICKUP"));
    }

    @Test
    void returnSuccessRecordsExactlyOneAuditEntryAndIsIdempotent() {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        operations.recordPickup(bookingId, fixture.owner(), 1000, 80);

        var first = operations.recordReturn(bookingId, fixture.owner(), 1200, 60, null, null);
        var second = operations.recordReturn(bookingId, fixture.owner(), 1200, 60, null, null);

        assertEquals(first.getId(), second.getId());
        assertEquals(BookingStatus.COMPLETED, bookingStatus(bookingId));
        assertEquals(VehicleStatus.AVAILABLE, vehicleStatus(fixture.vehicle()));
        assertEquals(1, auditCount(bookingId, "BOOKING_RETURN"));
        assertEquals(1, eventCount(bookingId, "BOOKING_RETURN"));
    }

    @Test
    void returnAuditFailureKeepsBookingInProgressWithoutPartialState() {
        var fixture = confirmed();
        UUID bookingId = fixture.booking().id();
        operations.recordPickup(bookingId, fixture.owner(), 1000, 80);
        failAudit("BOOKING_RETURN");

        assertThrows(IllegalStateException.class, () -> operations.recordReturn(bookingId, fixture.owner(), 1200, 60, null, null));

        assertEquals(BookingStatus.IN_PROGRESS, bookingStatus(bookingId));
        assertEquals(VehicleStatus.RENTED, vehicleStatus(fixture.vehicle()));
        assertEquals(1, ops.findByBookingIdOrderByCreatedAtAsc(bookingId).size(), "seule l'opération PICKUP doit subsister");
        assertEquals(0, eventCount(bookingId, "BOOKING_RETURN"));
        assertEquals(0, auditCount(bookingId, "BOOKING_RETURN"));
    }
}
