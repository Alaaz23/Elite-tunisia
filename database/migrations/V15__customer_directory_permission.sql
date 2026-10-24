INSERT INTO permissions (permission_key, description)
VALUES ('customers:read', 'Consulter les clients');

INSERT INTO role_permissions (role_key, permission_key)
VALUES ('SUPER_ADMIN', 'customers:read'), ('MANAGER', 'customers:read');
