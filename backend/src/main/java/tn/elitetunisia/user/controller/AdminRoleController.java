package tn.elitetunisia.user.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.elitetunisia.user.dto.RoleSummary;
import tn.elitetunisia.user.service.AdminRoleService;

@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('users:manage')")
public class AdminRoleController {
    private final AdminRoleService roles;

    public AdminRoleController(AdminRoleService roles) { this.roles = roles; }

    @GetMapping
    public List<RoleSummary> list() { return roles.list(); }
}