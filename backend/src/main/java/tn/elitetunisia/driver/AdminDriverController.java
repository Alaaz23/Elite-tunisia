package tn.elitetunisia.driver;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/admin/drivers")
@PreAuthorize("hasAuthority('drivers:manage')")
public class AdminDriverController {
    private final DriverService service;

    public AdminDriverController(DriverService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<DriverResponse> create(@jakarta.validation.Valid @RequestBody CreateDriverRequest req) {
        var d = service.create(req.firstName, req.lastName, req.phone, req.licenseNumber,
                req.licenseExpiry, Boolean.TRUE.equals(req.certified), req.notes);
        return ResponseEntity.ok(DriverResponse.from(d));
    }

    @GetMapping
    public ResponseEntity<List<DriverResponse>> list() { return ResponseEntity.ok(service.listAvailable().stream().map(DriverResponse::from).toList()); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) { service.deactivate(id); return ResponseEntity.noContent().build(); }

    public static class CreateDriverRequest {
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 80)
        public String firstName;

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 80)
        public String lastName;

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 40)
        public String phone;

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 80)
        public String licenseNumber;
        public java.time.Instant licenseExpiry;
        public Boolean certified;
        @jakarta.validation.constraints.Size(max = 2000)
        public String notes;
    }
}
