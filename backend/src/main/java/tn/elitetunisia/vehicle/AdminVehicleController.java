package tn.elitetunisia.vehicle;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.catalogue.dto.VehicleSearchRequest;
import tn.elitetunisia.catalogue.dto.VehicleSummary;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/vehicles")
@PreAuthorize("hasAuthority('vehicles:read')")
public class AdminVehicleController {
    private final VehicleService vehicles;
    private final AccountService accounts;
    public AdminVehicleController(VehicleService vehicles, AccountService accounts) { this.vehicles = vehicles; this.accounts = accounts; }

    @GetMapping
    public PagedResponse<VehicleSummary> search(@ModelAttribute VehicleSearchRequest filter) { return vehicles.adminSearch(filter); }

    @GetMapping("/{id}")
    public VehicleSummary get(@PathVariable UUID id) { return vehicles.adminGet(id); }

    @PostMapping
    @PreAuthorize("hasAuthority('vehicles:manage')")
    public VehicleSummary create(@Valid @RequestBody VehicleUpsertRequest request, Authentication auth, HttpServletRequest servlet) {
        return vehicles.create(request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicles:manage')")
    public VehicleSummary update(@PathVariable UUID id, @Valid @RequestBody VehicleUpsertRequest request,
            Authentication auth, HttpServletRequest servlet) {
        return vehicles.update(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('vehicles:manage')")
    public VehicleSummary status(@PathVariable UUID id, @Valid @RequestBody VehicleStatusRequest request,
            Authentication auth, HttpServletRequest servlet) {
        return vehicles.updateStatus(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicles:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        vehicles.delete(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}