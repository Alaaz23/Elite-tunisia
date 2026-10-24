INSERT INTO permissions (permission_key, description)
VALUES ('drivers:manage', 'Gérer les chauffeurs');

INSERT INTO role_permissions (role_key, permission_key)
VALUES ('SUPER_ADMIN', 'drivers:manage'), ('MANAGER', 'drivers:manage'), ('AGENT', 'drivers:manage');
