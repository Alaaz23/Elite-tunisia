package tn.elitetunisia.user.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.RoleEntity;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.user.dto.RoleSummary;

@Service
public class AdminRoleService {
    private final RoleRepository roles;

    public AdminRoleService(RoleRepository roles) { this.roles = roles; }

    @Transactional(readOnly = true)
    public List<RoleSummary> list() {
        return roles.findAll().stream()
                .sorted(Comparator.comparing(RoleEntity::getRoleKey))
                .map(role -> new RoleSummary(role.getRoleKey(), role.getDisplayName(),
                        role.getPermissions().stream().map(permission -> permission.getPermissionKey()).collect(java.util.stream.Collectors.toUnmodifiableSet())))
                .toList();
    }
}