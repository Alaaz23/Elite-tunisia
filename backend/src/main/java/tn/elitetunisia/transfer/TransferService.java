package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.driver.BookingDriverAssignmentEntity;
import tn.elitetunisia.driver.BookingDriverAssignmentRepository;
import tn.elitetunisia.driver.DriverAssignmentService;

@Service
public class TransferService {
    private final TransferRepository repo;
    private final BookingRepository bookings;
    private final BookingDriverAssignmentRepository assignments;
    private final DriverAssignmentService assignmentService;

    public TransferService(TransferRepository repo, BookingRepository bookings, BookingDriverAssignmentRepository assignments, DriverAssignmentService assignmentService) {
        this.repo = repo; this.bookings = bookings; this.assignments = assignments; this.assignmentService = assignmentService;
    }

    public TransferEntity create(TransferEntity t) { return repo.save(t); }

    public TransferEntity get(UUID id) { return repo.findById(id).orElseThrow(() -> new RuntimeException("TRANSFER_NOT_FOUND")); }

    public List<TransferEntity> listAll() { return repo.findAll(); }

    @Transactional
    public TransferEntity createForBooking(UUID bookingId, TransferEntity t) {
        BookingEntity booking = bookings.findById(bookingId).orElseThrow(() -> new RuntimeException("BOOKING_NOT_FOUND"));
        if (booking.getStatus() == tn.elitetunisia.booking.BookingStatus.CANCELLED) throw new RuntimeException("BOOKING_CANCELLED");
        Instant pickup = t.getPickupAt();
        if (pickup == null || pickup.isBefore(booking.getStartDateTime()) || pickup.isAfter(booking.getEndDateTime())) {
            throw new RuntimeException("TRANSFER_OUTSIDE_BOOKING_PERIOD");
        }
        // Reuse driver assignment if exists for this booking
        BookingDriverAssignmentEntity a = assignments.findByBookingId(bookingId);
        if (a != null) {
            t.setDriverId(a.getDriverId());
        } else if (t.getDriverId() != null) {
            // verify availability of provided driver
            long clashes = assignments.overlaps(t.getDriverId(), t.getPickupAt(), t.getPickupAt().plusSeconds(3600), bookingId);
            if (clashes > 0) throw new RuntimeException("DRIVER_NOT_AVAILABLE");
        }
        // ensure vehicle consistency
        t.setVehicleId(booking.getVehicle().getId());
        return repo.save(t);
    }
}
