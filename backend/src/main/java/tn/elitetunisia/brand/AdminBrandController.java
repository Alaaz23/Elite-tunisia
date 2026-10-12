package tn.elitetunisia.brand;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tn.elitetunisia.catalogue.dto.BrandSummary;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/brands")
@PreAuthorize("hasAuthority('brands:read')")
public class AdminBrandController {
    private final BrandService brands;
    private final AccountService accounts;
    public AdminBrandController(BrandService brands, AccountService accounts) { this.brands = brands; this.accounts = accounts; }

    @GetMapping
    public PagedResponse<BrandSummary> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return brands.adminList(page, size); }

    @PostMapping
    @PreAuthorize("hasAuthority('brands:manage')")
    public BrandSummary create(@Valid @RequestBody BrandUpsertRequest request, Authentication auth, HttpServletRequest servlet) {
        return brands.create(request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('brands:manage')")
    public BrandSummary update(@PathVariable UUID id, @Valid @RequestBody BrandUpsertRequest request,
            Authentication auth, HttpServletRequest servlet) {
        return brands.update(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('brands:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        brands.delete(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}