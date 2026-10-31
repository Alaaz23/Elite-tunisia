package tn.elitetunisia.transfer;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.driver.BookingDriverAssignmentRepository;
import tn.elitetunisia.driver.DriverRepository;

@Service
public class TransferService {
    /** Durée supposée d'un transfert pour détecter les conflits de chauffeur (hypothèse déjà présente dans l'ancien code). */
    static final long SLOT_SECONDS = 3600;

    private final TransferRepository repo;
    private final BookingRepository bookings;
    private final BookingDriverAssignmentRepository assignments;
    private final DriverRepository drivers;
    private final AuditService audit;

    public TransferService(TransferRepository repo, BookingRepository bookings, BookingDriverAssignmentRepository assignments,
            DriverRepository drivers, AuditService audit) {
        this.repo = repo; this.bookings = bookings; this.assignments = assignments; this.drivers = drivers; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public TransferEntity get(UUID id) { return repo.findById(id).orElseThrow(() -> DomainException.notFound("TRANSFER_NOT_FOUND")); }

    @Transactional(readOnly = true)
    public Page<TransferEntity> list(UUID bookingId, TransferStatus status, Pageable pageable) {
        if (bookingId != null && status != null) return repo.findByBookingIdAndStatus(bookingId, status, pageable);
        if (bookingId != null) return repo.findByBookingId(bookingId, pageable);
        if (status != null) return repo.findByStatus(status, pageable);
        return repo.findAll(pageable);
    }

    @Transactional
    public TransferEntity create(TransferRequest request, UUID actor, String ip) {
        BookingEntity booking = bookings.findLocked(request.bookingId()).orElseThrow(() -> DomainException.notFound("BOOKING_NOT_FOUND"));
        requirePlannable(booking);
        requireWithinBooking(booking, request.pickupAt());
        requireCapacity(booking, request.passengers());
        var assigned = assignments.findByBookingId(booking.getId());
        UUID driverId = request.driverId();
        if (assigned != null) {
            if (driverId != null && !driverId.equals(assigned.getDriverId())) throw DomainException.conflict("TRANSFER_DRIVER_MISMATCH");
            driverId = assigned.getDriverId();
        }
        var transfer = TransferEntity.forBooking(booking.getId(), booking.getVehicle().getId(), driverId, request.pickupAt(),
                request.pickupAddress().strip(), request.dropoffAddress().strip(), request.passengers());
        if (driverId != null) {
            requireDriverUsable(driverId, request.pickupAt(), assigned != null, booking.getId());
            requireNoTransferClash(driverId, request.pickupAt(), new UUID(0, 0));
        }
        var saved = repo.saveAndFlush(transfer);
        audit.recordAudit(actor, "TRANSFER_CREATED", "TRANSFER", saved.getId(), ip, "SUCCESS");
        return saved;
    }

    @Transactional
    public TransferEntity update(UUID id, TransferUpdateRequest request, UUID actor, String ip) {
        UUID bookingId = get(id).getBookingId();
        if (bookingId == null) throw DomainException.conflict("TRANSFER_LEGACY_BOOKING_REQUIRED");
        // Ordre de verrouillage identique à la création : réservation, puis transfert, puis chauffeur.
        BookingEntity booking = bookings.findLocked(bookingId).orElseThrow(() -> DomainException.notFound("BOOKING_NOT_FOUND"));
        var transfer = repo.findLockedById(id).orElseThrow(() -> DomainException.notFound("TRANSFER_NOT_FOUND"));
        if (!transfer.getStatus().editable()) throw DomainException.conflict("TRANSFER_NOT_EDITABLE");
        requirePlannable(booking);
        requireWithinBooking(booking, request.pickupAt());
        requireCapacity(booking, request.passengers());
        if (transfer.getDriverId() != null) {
            requireDriverUsable(transfer.getDriverId(), request.pickupAt(), true, booking.getId());
            requireNoTransferClash(transfer.getDriverId(), request.pickupAt(), transfer.getId());
        }
        transfer.reschedule(request.pickupAt(), request.pickupAddress().strip(), request.dropoffAddress().strip(), request.passengers());
        var saved = repo.saveAndFlush(transfer);
        audit.recordAudit(actor, "TRANSFER_UPDATED", "TRANSFER", saved.getId(), ip, "SUCCESS");
        return saved;
    }

    @Transactional
    public TransferEntity transition(UUID id, TransferStatus target, UUID actor, String ip) {
        var transfer = repo.findLockedById(id).orElseThrow(() -> DomainException.notFound("TRANSFER_NOT_FOUND"));
        if (transfer.getStatus() == target) return transfer;
        if (!transfer.getStatus().canTransitionTo(target)) throw DomainException.conflict("TRANSFER_INVALID_STATUS_TRANSITION");
        transfer.transitionTo(target);
        var saved = repo.saveAndFlush(transfer);
        audit.recordAudit(actor, "TRANSFER_" + target.name(), "TRANSFER", saved.getId(), ip, "SUCCESS");
        return saved;
    }

    private void requirePlannable(BookingEntity booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) throw DomainException.conflict("TRANSFER_BOOKING_STATUS");
    }

    private void requireWithinBooking(BookingEntity booking, Instant pickupAt) {
        if (pickupAt.isBefore(booking.getStartDateTime()) || pickupAt.isAfter(booking.getEndDateTime())) throw DomainException.conflict("TRANSFER_OUTSIDE_BOOKING_PERIOD");
    }

    private void requireCapacity(BookingEntity booking, int passengers) {
        if (passengers > booking.getVehicle().getPassengers()) throw DomainException.conflict("TRANSFER_TOO_MANY_PASSENGERS");
    }

    /** Chauffeur actif, permis valide à la prise en charge ; s'il n'est pas déjà celui de la réservation, il ne doit pas être pris ailleurs. */
    private void requireDriverUsable(UUID driverId, Instant pickupAt, boolean assignedToThisBooking, UUID bookingId) {
        var driver = drivers.findLockedById(driverId).orElseThrow(() -> DomainException.notFound("DRIVER_NOT_FOUND"));
        if (!driver.isActive()) throw DomainException.conflict("DRIVER_INACTIVE");
        if (driver.getLicenseExpiry() != null && driver.getLicenseExpiry().isBefore(pickupAt)) throw DomainException.conflict("DRIVER_LICENSE_EXPIRED");
        if (!assignedToThisBooking && assignments.overlaps(driverId, pickupAt, pickupAt.plusSeconds(SLOT_SECONDS), bookingId) > 0) {
            throw DomainException.conflict("DRIVER_NOT_AVAILABLE");
        }
    }

    private void requireNoTransferClash(UUID driverId, Instant pickupAt, UUID excludedId) {
        if (repo.countActiveForDriverBetween(driverId, excludedId, pickupAt.minusSeconds(SLOT_SECONDS), pickupAt.plusSeconds(SLOT_SECONDS)) > 0) {
            throw DomainException.conflict("DRIVER_NOT_AVAILABLE");
        }
    }
}
