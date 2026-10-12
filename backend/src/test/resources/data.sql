CREATE SEQUENCE IF NOT EXISTS booking_reference_seq;
CREATE SEQUENCE IF NOT EXISTS quote_reference_seq;
INSERT INTO roles (role_key, display_name) VALUES
    ('SUPER_ADMIN', 'Super administrateur'),
    ('MANAGER', 'Gestionnaire'),
    ('AGENT', 'Agent'),
    ('CONCIERGE', 'Conciergerie'),
    ('ACCOUNTANT', 'Comptabilite'),
    ('CUSTOMER', 'Client');

INSERT INTO permissions (permission_key, description) VALUES
    ('admin:access', 'Acceder au back-office'),
    ('users:read', 'Consulter les utilisateurs'),
    ('users:manage', 'Gerer les utilisateurs internes'),
    ('profile:read:self', 'Consulter son propre profil'),
    ('profile:update:self', 'Modifier son propre profil');

INSERT INTO role_permissions (role_key, permission_key)
SELECT 'SUPER_ADMIN', permission_key FROM permissions;

INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('MANAGER', 'admin:access'),
    ('AGENT', 'admin:access'),
    ('CONCIERGE', 'admin:access'),
    ('ACCOUNTANT', 'admin:access'),
    ('CUSTOMER', 'profile:read:self'),
    ('CUSTOMER', 'profile:update:self');
