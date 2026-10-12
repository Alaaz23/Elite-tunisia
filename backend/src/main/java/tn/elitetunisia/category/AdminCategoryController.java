package tn.elitetunisia.category;

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
import tn.elitetunisia.catalogue.dto.CategorySummary;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasAuthority('categories:read')")
public class AdminCategoryController {
    private final CategoryService categories;
    private final AccountService accounts;
    public AdminCategoryController(CategoryService categories, AccountService accounts) { this.categories = categories; this.accounts = accounts; }

    @GetMapping
    public PagedResponse<CategorySummary> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return categories.adminList(page, size); }

    @PostMapping
    @PreAuthorize("hasAuthority('categories:manage')")
    public CategorySummary create(@Valid @RequestBody CategoryUpsertRequest request, Authentication auth, HttpServletRequest servlet) {
        return categories.create(request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('categories:manage')")
    public CategorySummary update(@PathVariable UUID id, @Valid @RequestBody CategoryUpsertRequest request,
            Authentication auth, HttpServletRequest servlet) {
        return categories.update(id, request, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('categories:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) {
        categories.delete(id, accounts.current(auth.getName()).id(), servlet.getRemoteAddr());
    }
}