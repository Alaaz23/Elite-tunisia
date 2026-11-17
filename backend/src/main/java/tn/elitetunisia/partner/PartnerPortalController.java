package tn.elitetunisia.partner;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.elitetunisia.partner.PartnerDtos.GuaranteeSummary;
import tn.elitetunisia.partner.PartnerDtos.PartnerSummary;
import tn.elitetunisia.partner.PartnerDtos.PartnerVehicleSummary;
import tn.elitetunisia.user.service.AccountService;

/** Espace partenaire en lecture seule, limité au partenaire lié à l'utilisateur courant. */
@RestController
@RequestMapping("/api/partner")
@PreAuthorize("hasRole('PARTNER') and hasAuthority('partner-portal:read')")
public class PartnerPortalController {
    private final PartnerService partners;
    private final GuaranteeService guarantees;
    private final AccountService accounts;

    public PartnerPortalController(PartnerService partners, GuaranteeService guarantees, AccountService accounts) {
        this.partners = partners;
        this.guarantees = guarantees;
        this.accounts = accounts;
    }

    @GetMapping("/me")
    public PartnerSummary me(Authentication auth) {
        return partners.ownProfile(accounts.current(auth.getName()).id());
    }

    @GetMapping("/vehicles")
    public List<PartnerVehicleSummary> vehicles(Authentication auth) {
        return partners.ownVehicles(accounts.current(auth.getName()).id());
    }

    @GetMapping("/vehicles/{vehicleId}")
    public PartnerVehicleSummary vehicle(@PathVariable UUID vehicleId, Authentication auth) {
        return partners.ownVehicle(accounts.current(auth.getName()).id(), vehicleId);
    }

    @GetMapping("/guarantees")
    public List<GuaranteeSummary> guarantees(Authentication auth) {
        return guarantees.ownGuarantees(accounts.current(auth.getName()).id());
    }
}
