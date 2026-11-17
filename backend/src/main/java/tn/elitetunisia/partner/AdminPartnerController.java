package tn.elitetunisia.partner;

import tn.elitetunisia.common.web.AdminPageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
import tn.elitetunisia.partner.PartnerDtos.AssignVehicleRequest;
import tn.elitetunisia.partner.PartnerDtos.AssignmentSummary;
import tn.elitetunisia.partner.PartnerDtos.PartnerRequest;
import tn.elitetunisia.partner.PartnerDtos.PartnerSummary;
import tn.elitetunisia.partner.PartnerDtos.PartnerVehicleSummary;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/partners")
@PreAuthorize("hasAuthority('partners:read')")
public class AdminPartnerController {
    private final PartnerService partners;
    private final AccountService accounts;

    public AdminPartnerController(PartnerService partners, AccountService accounts) {
        this.partners = partners;
        this.accounts = accounts;
    }

    @GetMapping
    public PagedResponse<PartnerSummary> list(Pageable pageable) {
        Page<PartnerSummary> page = partners.list(AdminPageRequest.of(pageable, Set.of("name", "email", "city", "active", "createdAt"), "createdAt"));
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @GetMapping("/{id}")
    public PartnerSummary get(@PathVariable UUID id) {
        return partners.get(id);
    }

    @GetMapping("/{id}/vehicles")
    public List<PartnerVehicleSummary> vehicles(@PathVariable UUID id) {
        return partners.vehiclesOf(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('partners:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerSummary create(@Valid @RequestBody PartnerRequest request, Authentication auth, HttpServletRequest servlet) {
        return partners.create(request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('partners:manage')")
    public PartnerSummary update(@PathVariable UUID id, @Valid @RequestBody PartnerRequest request,
            Authentication auth, HttpServletRequest servlet) {
        return partners.update(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('partners:manage')")
    public PartnerSummary deactivate(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        return partners.deactivate(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('partners:manage')")
    public PartnerSummary activate(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        return partners.activate(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}
