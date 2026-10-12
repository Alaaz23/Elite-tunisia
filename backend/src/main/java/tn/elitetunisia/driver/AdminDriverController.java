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
    public ResponseEntity<DriverEntity> create(@RequestBody CreateDriverRequest req) {
        var d = service.create(req.firstName, req.lastName, req.phone);
        return ResponseEntity.ok(d);
    }

    @GetMapping
    public ResponseEntity<List<DriverEntity>> list() { return ResponseEntity.ok(service.listAvailable()); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) { service.deactivate(id); return ResponseEntity.noContent().build(); }

    public static class CreateDriverRequest { public String firstName; public String lastName; public String phone; }
}
