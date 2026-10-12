package tn.elitetunisia.driver;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;

@Service
public class DriverAssignmentService {
    private final DriverRepository drivers;
    private final BookingRepository bookings;
    private final BookingDriverAssignmentRepository assignments;
    private final JdbcTemplate jdbc;

    public DriverAssignmentService(DriverRepository drivers, BookingRepository bookings, BookingDriverAssignmentRepository assignments, JdbcTemplate jdbc) {
        this.drivers = drivers; this.bookings = bookings; this.assignments = assignments; this.jdbc = jdbc;
    }

    @Transactional
    public BookingDriverAssignmentEntity assignDriver(UUID bookingId, UUID driverId) {
        return assignDriver(bookingId, driverId, false);
    }

    @Transactional
    public BookingDriverAssignmentEntity assignDriver(UUID bookingId, UUID driverId, boolean requireCertified) {
        BookingEntity booking = bookings.findById(bookingId).orElseThrow(() -> new RuntimeException("BOOKING_NOT_FOUND"));
        if (booking.getStatus() != tn.elitetunisia.booking.BookingStatus.PENDING && booking.getStatus() != tn.elitetunisia.booking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("BOOKING_STATUS_NO_DRIVER");
        }
        var driver = drivers.findById(driverId).orElseThrow(() -> new RuntimeException("DRIVER_NOT_FOUND"));
        if (!driver.isActive()) throw new RuntimeException("DRIVER_INACTIVE");
        // business validations: license expiry and certification
        if (driver.getLicenseExpiry() != null && driver.getLicenseExpiry().isBefore(Instant.now())) throw new RuntimeException("DRIVER_LICENSE_EXPIRED");
        if (requireCertified && !driver.isCertified()) throw new RuntimeException("DRIVER_NOT_CERTIFIED");
        Instant start = booking.getStartDateTime(); Instant end = booking.getEndDateTime();
        // Acquire a PostgreSQL advisory lock on the driver id to prevent concurrent assignments.
        // This is a best-effort, non-invasive protection: it requires Postgres to be used in production.
        try {
            if (jdbc != null) {
                // use hashtext to derive a bigint from the driver's UUID string
                String sql = "SELECT pg_advisory_xact_lock(hashtext(? )::bigint)";
                jdbc.execute((org.springframework.jdbc.core.StatementCallback<Void>) stmt -> {
                    try (var ps = stmt.getConnection().prepareStatement(sql)) { ps.setString(1, driverId.toString()); ps.execute(); }
                    return null;
                });
            }
        } catch (Exception ex) {
            // If advisory lock is not available, fall back to proceed without it — caller should run integration tests
        }
        long clashes = assignments.overlaps(driverId, start, end, bookingId);
        if (clashes > 0) throw new RuntimeException("DRIVER_NOT_AVAILABLE");
        // remove any previous assignment for this booking
        var prev = assignments.findByBookingId(bookingId);
        if (prev != null) assignments.delete(prev);
        var a = assignments.save(new BookingDriverAssignmentEntity(bookingId, driverId, start, end));
        return a;
    }

    @Transactional
    public void unassignDriver(UUID bookingId) {
        var a = assignments.findByBookingId(bookingId);
        if (a == null) throw new RuntimeException("ASSIGNMENT_NOT_FOUND");
        assignments.delete(a);
    }
}
