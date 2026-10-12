package tn.elitetunisia.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class PermissionEntity {
    @Id
    @Column(name = "permission_key", length = 80)
    private String permissionKey;

    @Column(nullable = false, length = 160)
    private String description;

    protected PermissionEntity() { }

    public PermissionEntity(String permissionKey, String description) {
        this.permissionKey = permissionKey;
        this.description = description;
    }

    public String getPermissionKey() { return permissionKey; }
}