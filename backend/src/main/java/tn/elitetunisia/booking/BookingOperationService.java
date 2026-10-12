package tn.elitetunisia.booking;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.elitetunisia.vehicle.VehicleRepository;
import tn.elitetunisia.vehicle.VehicleStatus;
import tn.elitetunisia.common.audit.AuditService;
import java.util.List;

@Service
public class BookingOperationService {
    private final BookingOperationRepository ops;
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final AuditService audit;

    public BookingOperationService(BookingOperationRepository ops, BookingRepository bookings, VehicleRepository vehicles, AuditService audit) {
        this.ops = ops; this.bookings = bookings; this.vehicles = vehicles; this.audit = audit;
    }

    @Transactional
    public BookingOperationEntity recordPickup(UUID bookingId, UUID actorId, Integer odometerStart, Integer fuelStart) {
        var booking = bookings.findLocked(bookingId).orElseThrow(() -> new BookingException("BOOKING_NOT_FOUND", "Booking not found"));
        if (booking.getStatus() != BookingStatus.CONFIRMED) throw new BookingException("INVALID_OPERATION", "Booking must be CONFIRMED to pickup.");

        // idempotence: if a pickup already exists for this booking, return it
        List<BookingOperationEntity> existing = ops.findByBookingIdOrderByCreatedAtAsc(bookingId);
        for (var e : existing) if ("PICKUP".equals(e.getAction())) return e;

        // set vehicle status to RENTED and validate vehicle
        var vehicle = vehicles.findLocked(booking.getVehicle().getId()).orElseThrow(() -> new BookingException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        if (odometerStart == null || odometerStart < 0) throw new BookingException("INVALID_DATA", "Invalid odometer value.");
        // if there are previous operations, ensure odometer is non-decreasing
        for (var e : existing) {
            Integer prev = e.getOdometerEnd() == null ? e.getOdometerStart() : e.getOdometerEnd();
            if (prev != null && odometerStart < prev) throw new BookingException("INVALID_DATA", "Odometer cannot be less than previous value.");
        }

        vehicle.setStatus(VehicleStatus.RENTED);
        BookingOperationEntity op = new BookingOperationEntity(bookingId, actorId, "PICKUP", odometerStart, null, fuelStart, null, null, BigDecimal.ZERO);
        ops.saveAndFlush(op);

        // transition booking to IN_PROGRESS
        booking.transition(BookingStatus.IN_PROGRESS, java.time.Instant.now());
        bookings.flush();

        // audit
        try { audit.recordAudit(actorId, "BOOKING_PICKUP", "BOOKING", bookingId, null, "SUCCESS"); } catch (Exception ignore) {}

        return op;
    }

    @Transactional
    public BookingOperationEntity recordReturn(UUID bookingId, UUID actorId, Integer odometerEnd, Integer fuelEnd, String damages, BigDecimal extraCharges) {
        var booking = bookings.findLocked(bookingId).orElseThrow(() -> new BookingException("BOOKING_NOT_FOUND", "Booking not found"));

        // require pickup exists
        List<BookingOperationEntity> existing = ops.findByBookingIdOrderByCreatedAtAsc(bookingId);
        BookingOperationEntity pickup = null;
        for (var e : existing) if ("PICKUP".equals(e.getAction())) { pickup = e; break; }
        if (pickup == null) throw new BookingException("INVALID_OPERATION", "Pickup must exist before return.");

        // idempotence: if a return already exists, return it
        for (var e : existing) if ("RETURN".equals(e.getAction())) return e;

        if (odometerEnd == null || odometerEnd < 0) throw new BookingException("INVALID_DATA", "Invalid odometer value.");
        if (pickup.getOdometerStart() != null && odometerEnd < pickup.getOdometerStart()) throw new BookingException("INVALID_DATA", "Return odometer cannot be less than pickup.");

        var vehicle = vehicles.findLocked(booking.getVehicle().getId()).orElseThrow(() -> new BookingException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        // decide vehicle status after return
        if (damages != null && !damages.isBlank()) vehicle.setStatus(VehicleStatus.MAINTENANCE); else vehicle.setStatus(VehicleStatus.AVAILABLE);

        BookingOperationEntity op = new BookingOperationEntity(bookingId, actorId, "RETURN", null, odometerEnd, null, fuelEnd, damages, extraCharges == null ? BigDecimal.ZERO : extraCharges);
        ops.saveAndFlush(op);

        // transition booking to COMPLETED
        booking.transition(BookingStatus.COMPLETED, java.time.Instant.now());
        bookings.flush();

        // audit
        try { audit.recordAudit(actorId, "BOOKING_RETURN", "BOOKING", bookingId, null, "SUCCESS"); } catch (Exception ignore) {}

        return op;
    }
}
