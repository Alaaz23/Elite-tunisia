package tn.elitetunisia.auth.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    @Column(name = "role_key", length = 40)
    private String roleKey;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @ManyToMany(fetch = FetchType.LAZY)
    @jakarta.persistence.JoinTable(name = "role_permissions",
            joinColumns = @jakarta.persistence.JoinColumn(name = "role_key"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "permission_key"))
    private Set<PermissionEntity> permissions = new HashSet<>();

    protected RoleEntity() { }

    public RoleEntity(String roleKey, String displayName) {
        this.roleKey = roleKey;
        this.displayName = displayName;
    }

    public String getRoleKey() { return roleKey; }
    public String getDisplayName() { return displayName; }
    public Set<PermissionEntity> getPermissions() { return permissions; }
    public void addPermission(PermissionEntity permission) { permissions.add(permission); }
}