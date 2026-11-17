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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeRequest;
import tn.elitetunisia.partner.PartnerDtos.GuaranteeSummary;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/guarantees")
@PreAuthorize("hasAuthority('partners:read')")
public class AdminGuaranteeController {
    private final GuaranteeService guarantees;
    private final AccountService accounts;

    public AdminGuaranteeController(GuaranteeService guarantees, AccountService accounts) {
        this.guarantees = guarantees;
        this.accounts = accounts;
    }

    @GetMapping
    public PagedResponse<GuaranteeSummary> list(Pageable pageable) {
        Page<GuaranteeSummary> page = guarantees.list(AdminPageRequest.of(pageable, Set.of("startsAt", "endsAt", "createdAt", "withdrawnAt"), "createdAt"));
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @GetMapping("/{id}")
    public GuaranteeSummary get(@PathVariable UUID id) {
        return guarantees.get(id);
    }

    @GetMapping("/by-vehicle/{vehicleId}")
    public List<GuaranteeSummary> byVehicle(@PathVariable UUID vehicleId) {
        return guarantees.listForVehicle(vehicleId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('partners:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public GuaranteeSummary create(@Valid @RequestBody GuaranteeRequest request, Authentication auth,
            HttpServletRequest servlet) {
        return guarantees.create(request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('partners:manage')")
    public GuaranteeSummary withdraw(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        return guarantees.withdraw(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}
