package tn.elitetunisia.common.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.auth.entity.UserEntity;
import tn.elitetunisia.auth.entity.PermissionEntity;
import tn.elitetunisia.auth.entity.RoleEntity;
import tn.elitetunisia.auth.repository.PermissionRepository;
import tn.elitetunisia.auth.repository.RoleRepository;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.employee.EmployeeEntity;
import tn.elitetunisia.employee.EmployeeRepository;

@Component
@Profile("test")
@ConditionalOnProperty(prefix = "app.test-seed", name = "admin", havingValue = "true")
public class TestProfileAdminSeeder implements ApplicationRunner {
    public static final String EMAIL = "superadmin@example.test";
    public static final String PASSWORD = "Test-Admin-123!";

    private final UserRepository users;
    private final RoleRepository roles;
    private final EmployeeRepository employees;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;

    public TestProfileAdminSeeder(UserRepository users, RoleRepository roles, PermissionRepository permissions, EmployeeRepository employees,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoleCatalog();
        if (users.existsByEmailIgnoreCase(EMAIL)) return;
        UserEntity admin = new UserEntity(EMAIL, passwordEncoder.encode(PASSWORD));
        admin.addRole(roles.findById("SUPER_ADMIN").orElseThrow());
        admin = users.saveAndFlush(admin);
        employees.save(new EmployeeEntity(admin, "EMP-TEST-ADMIN", "Super administrateur", "Test", "Admin", "+216 20 000 000"));
    }

    private void seedRoleCatalog() {
        String[][] roleNames = {
                {"SUPER_ADMIN", "Super administrateur"}, {"MANAGER", "Gestionnaire"}, {"AGENT", "Agent"},
                {"CONCIERGE", "Conciergerie"}, {"ACCOUNTANT", "Comptabilite"}, {"CUSTOMER", "Client"}
        };
        for (String[] role : roleNames) {
            roles.findById(role[0]).orElseGet(() -> roles.save(new RoleEntity(role[0], role[1])));
        }
        String[][] permissionNames = {
                {"admin:access", "Acceder au back-office"}, {"users:read", "Consulter les utilisateurs"},
                {"users:manage", "Gerer les utilisateurs internes"}, {"profile:read:self", "Consulter son profil"},
                {"profile:update:self", "Modifier son profil"}, {"vehicles:read", "Consulter les véhicules"},
                {"vehicles:manage", "Gérer les véhicules"}, {"brands:read", "Consulter les marques"},
                {"brands:manage", "Gérer les marques"}, {"categories:read", "Consulter les catégories"},
                {"categories:manage", "Gérer les catégories"}, {"bookings:read", "Consulter les réservations"},
                {"bookings:create", "Créer ses demandes"}, {"bookings:manage", "Gérer les réservations"}, {"bookings:cancel", "Annuler les réservations"},
                {"quotes:read", "Consulter les devis"}, {"quotes:create", "Créer les devis"}, {"quotes:manage", "Gérer les devis"},
                {"quotes:send", "Envoyer les devis"}, {"quotes:accept", "Accepter ses devis"}, {"quotes:reject", "Refuser ses devis"},
                {"payments:create", "Payer ses devis acceptés"}, {"invoices:read", "Consulter ses factures"},
                {"drivers:manage", "Gérer les chauffeurs"}, {"customers:read", "Consulter les clients"},
                {"services:read", "Consulter le catalogue de services"}, {"services:manage", "Créer et gérer les services"}
        };
        for (String[] permission : permissionNames) {
            permissions.findById(permission[0]).orElseGet(() -> permissions.save(new PermissionEntity(permission[0], permission[1])));
        }
        RoleEntity superAdmin = roles.findById("SUPER_ADMIN").orElseThrow();
        permissions.findAll().forEach(superAdmin::addPermission);
        roles.save(superAdmin);
        for (String roleKey : java.util.List.of("MANAGER", "AGENT", "CONCIERGE", "ACCOUNTANT")) {
            RoleEntity staffRole = roles.findById(roleKey).orElseThrow();
            staffRole.addPermission(permissions.findById("admin:access").orElseThrow());
            staffRole.addPermission(permissions.findById("bookings:read").orElseThrow());
            staffRole.addPermission(permissions.findById("quotes:read").orElseThrow());
            if (roleKey.equals("MANAGER") || roleKey.equals("AGENT")) {
                staffRole.addPermission(permissions.findById("bookings:manage").orElseThrow());
                staffRole.addPermission(permissions.findById("bookings:cancel").orElseThrow());
                for (String permission : java.util.List.of("quotes:create", "quotes:manage", "quotes:send")) staffRole.addPermission(permissions.findById(permission).orElseThrow());
                staffRole.addPermission(permissions.findById("drivers:manage").orElseThrow());
            }
            if (roleKey.equals("MANAGER")) {
                staffRole.addPermission(permissions.findById("customers:read").orElseThrow());
            }
            for (String permission : java.util.List.of("vehicles:read", "brands:read", "categories:read", "services:read")) {
                staffRole.addPermission(permissions.findById(permission).orElseThrow());
            }
            if (roleKey.equals("MANAGER")) {
                for (String permission : java.util.List.of("vehicles:manage", "brands:manage", "categories:manage", "services:manage")) {
                    staffRole.addPermission(permissions.findById(permission).orElseThrow());
                }
            }
            roles.save(staffRole);
        }
        RoleEntity customer = roles.findById("CUSTOMER").orElseThrow();
        for (String permission : java.util.List.of("bookings:read", "bookings:create", "bookings:cancel", "quotes:read", "quotes:accept", "quotes:reject", "payments:create", "invoices:read")) {
            customer.addPermission(permissions.findById(permission).orElseThrow());
        }
        customer.addPermission(permissions.findById("profile:read:self").orElseThrow());
        customer.addPermission(permissions.findById("profile:update:self").orElseThrow());
        roles.save(customer);
    }
}