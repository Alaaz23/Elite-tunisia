package tn.elitetunisia.partner;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tn.elitetunisia.partner.PartnerDtos.AssignVehicleRequest;
import tn.elitetunisia.partner.PartnerDtos.AssignmentSummary;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/vehicles/{vehicleId}/partner-assignments")
public class VehiclePartnerAssignmentController {
    private final PartnerService partners;
    private final AccountService accounts;

    public VehiclePartnerAssignmentController(PartnerService partners, AccountService accounts) {
        this.partners = partners;
        this.accounts = accounts;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('partners:read')")
    public List<AssignmentSummary> history(@PathVariable UUID vehicleId) {
        return partners.history(vehicleId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('partners:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentSummary assign(@PathVariable UUID vehicleId, @Valid @RequestBody AssignVehicleRequest request,
            Authentication auth, HttpServletRequest servlet) {
        return partners.assign(vehicleId, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}
