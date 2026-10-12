package tn.elitetunisia.user.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import tn.elitetunisia.user.dto.CreateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateUserStatusRequest;
import tn.elitetunisia.user.dto.UserSummary;
import tn.elitetunisia.user.service.AccountService;
import tn.elitetunisia.user.service.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('users:read')")
public class AdminUserController {
    private final AdminUserService adminUsers;
    private final AccountService accounts;

    public AdminUserController(AdminUserService adminUsers, AccountService accounts) {
        this.adminUsers = adminUsers;
        this.accounts = accounts;
    }

    @GetMapping
    public Page<UserSummary> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminUsers.list(page, size);
    }

    @GetMapping("/{id}")
    public UserSummary get(@PathVariable UUID id) { return accounts.findById(id); }

    @PostMapping
    @PreAuthorize("hasAuthority('users:manage')")
    public UserSummary create(@Valid @RequestBody CreateEmployeeRequest request, Authentication authentication,
            HttpServletRequest servletRequest) {
        return adminUsers.create(request, actorId(authentication), servletRequest.getRemoteAddr());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users:manage')")
    public UserSummary update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        return adminUsers.update(id, request, actorId(authentication), servletRequest.getRemoteAddr());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('users:manage')")
    public UserSummary updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        return adminUsers.updateStatus(id, request, actorId(authentication), servletRequest.getRemoteAddr());
    }

    private UUID actorId(Authentication authentication) {
        return accounts.current(authentication.getName()).id();
    }
}