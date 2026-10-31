package tn.elitetunisia.user.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.RoleEntity;
import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.RefreshTokenRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.common.exception.EmailAlreadyRegisteredException;
import tn.elitetunisia.employee.EmployeeEntity;
import tn.elitetunisia.employee.EmployeeRepository;
import tn.elitetunisia.user.dto.CreateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateEmployeeRequest;
import tn.elitetunisia.user.dto.UpdateUserStatusRequest;
import tn.elitetunisia.user.dto.UserSummary;

@Service
public class AdminUserService {
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Set<String> INTERNAL_ROLES = Set.of("SUPER_ADMIN", "MANAGER", "AGENT", "CONCIERGE", "ACCOUNTANT");
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AccountService accounts;

    public AdminUserService(UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens, EmployeeRepository employees,
            PasswordEncoder passwordEncoder, AuditService audit, AccountService accounts) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> list(int page, int size) {
        return users.findAll(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))
                .map(user -> accounts.findById(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserSummary get(UUID id) {
        return accounts.findById(id);
    }

    @Transactional
    public UserSummary create(CreateEmployeeRequest request, UUID actorId, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new EmailAlreadyRegisteredException();
        RoleEntity role = internalRole(request.role());
        String employeeNumber = "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UserEntity user = new UserEntity(email, passwordEncoder.encode(request.password()));
        user.addRole(role);
        user = users.save(user);
        employees.save(new EmployeeEntity(user, employeeNumber, request.jobTitle().trim(), request.firstName().trim(),
                request.lastName().trim(), request.phone().trim()));
        audit.recordAudit(actorId, "USER_CREATED", "USER", user.getId(), ipAddress, "SUCCESS");
        return accounts.findById(user.getId());
    }

    @Transactional
    public UserSummary update(UUID id, UpdateEmployeeRequest request, UUID actorId, String ipAddress) {
        roles.lockSuperAdminRole();
        UserEntity user = users.findById(id).orElseThrow(UserNotFoundException::new);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!user.getEmail().equalsIgnoreCase(email) && users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        RoleEntity role = internalRole(request.role());
        rejectIfLastSuperAdminLost(id, SUPER_ADMIN.equals(role.getRoleKey()));
        boolean roleChanged = !user.getRoleKeys().contains(role.getRoleKey()) || user.getRoleKeys().size() != 1;
        user.setEmail(email);
        if (roleChanged) {
            user.removeRoles();
            user.addRole(role);
            audit.recordAudit(actorId, "ROLE_CHANGED", "USER", user.getId(), ipAddress, "SUCCESS");
        }
        EmployeeEntity employee = employees.findById(id).orElseThrow(UserNotFoundException::new);
        employee.update(request.firstName().trim(), request.lastName().trim(), request.phone().trim(), request.jobTitle().trim());
        audit.recordAudit(actorId, "USER_UPDATED", "USER", user.getId(), ipAddress, "SUCCESS");
        return accounts.findById(id);
    }

    @Transactional
    public UserSummary updateStatus(UUID id, UpdateUserStatusRequest request, UUID actorId, String ipAddress) {
        roles.lockSuperAdminRole();
        UserEntity user = users.findById(id).orElseThrow(UserNotFoundException::new);
        rejectIfLastSuperAdminLost(id, request.enabled());
        boolean wasEnabled = user.isEnabled();
        user.setEnabled(request.enabled());
        if (wasEnabled && !request.enabled()) {
            refreshTokens.revokeAllByUserId(id, java.time.Instant.now());
            audit.recordAudit(actorId, "USER_DISABLED", "USER", id, ipAddress, "SUCCESS");
        } else {
            audit.recordAudit(actorId, "USER_UPDATED", "USER", id, ipAddress, "SUCCESS");
        }
        return accounts.findById(id);
    }

    /** L'appelant détient le verrou du rôle SUPER_ADMIN : le décompte reste exact face aux opérations concurrentes. */
    private void rejectIfLastSuperAdminLost(UUID targetId, boolean staysActiveSuperAdmin) {
        if (!staysActiveSuperAdmin && users.isActiveSuperAdmin(targetId) && users.countActiveSuperAdmins() <= 1) {
            throw new DomainException("LAST_SUPER_ADMIN", HttpStatus.CONFLICT);
        }
    }

    private RoleEntity internalRole(String roleKey) {
        String normalized = roleKey.trim().toUpperCase(Locale.ROOT);
        if (!INTERNAL_ROLES.contains(normalized)) throw new InvalidUserRoleException();
        return roles.findById(normalized).orElseThrow(InvalidUserRoleException::new);
    }
}