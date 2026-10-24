package tn.elitetunisia.driver;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasAuthority('bookings:manage')")
public class AdminDriverAssignmentController {
    private final DriverAssignmentService service;

    public AdminDriverAssignmentController(DriverAssignmentService service) { this.service = service; }

    @GetMapping("/{bookingId}/driver")
    public ResponseEntity<AssignResponse> get(@PathVariable UUID bookingId) {
        var assignment = service.assignment(bookingId);
        return assignment == null ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(new AssignResponse(assignment.getId(), assignment.getDriverId(), assignment.getBookingId()));
    }

    @PostMapping("/{bookingId}/driver")
    public ResponseEntity<?> assign(@PathVariable UUID bookingId, @RequestBody AssignDriverRequest req) {
        boolean requireCertified = req.requireCertified != null ? req.requireCertified : false;
        var a = service.assignDriver(bookingId, req.driverId, requireCertified);
        return ResponseEntity.ok(new AssignResponse(a.getId(), a.getDriverId(), a.getBookingId()));
    }

    @DeleteMapping("/{bookingId}/driver")
    public ResponseEntity<Void> unassign(@PathVariable UUID bookingId) {
        service.unassignDriver(bookingId);
        return ResponseEntity.noContent().build();
    }

    public static class AssignDriverRequest { public UUID driverId; public Boolean requireCertified; }
    public record AssignResponse(UUID id, UUID driverId, UUID bookingId) {}
}
