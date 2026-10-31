package tn.elitetunisia.driver;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.common.exception.DomainException;

@Service
public class DriverAssignmentService {
    private final DriverRepository drivers;
    private final BookingRepository bookings;
    private final BookingDriverAssignmentRepository assignments;

    public DriverAssignmentService(DriverRepository drivers, BookingRepository bookings, BookingDriverAssignmentRepository assignments) {
        this.drivers = drivers; this.bookings = bookings; this.assignments = assignments;
    }

    @Transactional
    public BookingDriverAssignmentEntity assignDriver(UUID bookingId, UUID driverId) {
        return assignDriver(bookingId, driverId, false);
    }

    @Transactional
    public BookingDriverAssignmentEntity assignDriver(UUID bookingId, UUID driverId, boolean requireCertified) {
        BookingEntity booking = bookings.findLocked(bookingId).orElseThrow(() -> DomainException.notFound("BOOKING_NOT_FOUND"));
        if (booking.getStatus() != tn.elitetunisia.booking.BookingStatus.PENDING && booking.getStatus() != tn.elitetunisia.booking.BookingStatus.CONFIRMED) {
            throw DomainException.conflict("BOOKING_STATUS_NO_DRIVER");
        }
        var driver = drivers.findLockedById(driverId).orElseThrow(() -> DomainException.notFound("DRIVER_NOT_FOUND"));
        if (!driver.isActive()) throw DomainException.conflict("DRIVER_INACTIVE");
        // business validations: license expiry and certification
        if (driver.getLicenseExpiry() != null && driver.getLicenseExpiry().isBefore(Instant.now())) throw DomainException.conflict("DRIVER_LICENSE_EXPIRED");
        if (requireCertified && !driver.isCertified()) throw DomainException.conflict("DRIVER_NOT_CERTIFIED");
        Instant start = booking.getStartDateTime(); Instant end = booking.getEndDateTime();
        // Locking the driver row serializes concurrent assignments across supported databases.
        long clashes = assignments.overlaps(driverId, start, end, bookingId);
        if (clashes > 0) throw DomainException.conflict("DRIVER_NOT_AVAILABLE");
        // remove any previous assignment for this booking
        var prev = assignments.findByBookingId(bookingId);
        if (prev != null) assignments.delete(prev);
        var a = assignments.save(new BookingDriverAssignmentEntity(bookingId, driverId, start, end));
        return a;
    }

    @Transactional
    public void unassignDriver(UUID bookingId) {
        var a = assignments.findByBookingId(bookingId);
        if (a == null) throw DomainException.notFound("ASSIGNMENT_NOT_FOUND");
        assignments.delete(a);
    }

    @Transactional(readOnly = true)
    public BookingDriverAssignmentEntity assignment(UUID bookingId) {
        if (!bookings.existsById(bookingId)) throw DomainException.notFound("BOOKING_NOT_FOUND");
        return assignments.findByBookingId(bookingId);
    }
}
