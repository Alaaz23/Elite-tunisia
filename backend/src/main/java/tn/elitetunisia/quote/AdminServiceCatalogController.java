package tn.elitetunisia.quote;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;

/** Catalogue de services : lecture pour le personnel (services:read), écriture pour services:manage. Aucune suppression. */
@RestController
@RequestMapping("/api/admin/services")
@PreAuthorize("hasAuthority('services:read')")
@Validated
public class AdminServiceCatalogController {
    private final ServiceCatalogService service;
    private final AccountService accounts;

    public AdminServiceCatalogController(ServiceCatalogService service, AccountService accounts) { this.service = service; this.accounts = accounts; }

    @GetMapping
    public PagedResponse<ServiceResponse> list(@RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 120) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        var result = service.list(search, active, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return new PagedResponse<>(result.map(ServiceResponse::from).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable UUID id) { return ServiceResponse.from(service.get(id)); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('services:manage')")
    public ServiceResponse create(@Valid @RequestBody ServiceCreateRequest request, Authentication auth, HttpServletRequest servlet) {
        return ServiceResponse.from(service.create(request, actor(auth), servlet.getRemoteAddr()));
    }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('services:manage')")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceUpdateRequest request, Authentication auth, HttpServletRequest servlet) {
        return ServiceResponse.from(service.update(id, request, actor(auth), servlet.getRemoteAddr()));
    }

    @PostMapping("/{id}/activate") @PreAuthorize("hasAuthority('services:manage')")
    public ServiceResponse activate(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        return ServiceResponse.from(service.setActive(id, true, actor(auth), servlet.getRemoteAddr()));
    }

    @PostMapping("/{id}/deactivate") @PreAuthorize("hasAuthority('services:manage')")
    public ServiceResponse deactivate(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        return ServiceResponse.from(service.setActive(id, false, actor(auth), servlet.getRemoteAddr()));
    }

    private UUID actor(Authentication auth) { return accounts.current(auth.getName()).id(); }
}
