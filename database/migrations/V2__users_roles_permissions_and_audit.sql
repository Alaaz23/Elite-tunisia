CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_key VARCHAR(40) NOT NULL REFERENCES roles(role_key) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_key)
);

INSERT INTO user_roles (user_id, role_key)
SELECT id, role_key FROM users;

ALTER TABLE users DROP COLUMN role_key;

ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL,
    ALTER COLUMN phone SET NOT NULL;

ALTER TABLE employees
    ADD COLUMN first_name VARCHAR(80) NOT NULL,
    ADD COLUMN last_name VARCHAR(80) NOT NULL,
    ADD COLUMN phone VARCHAR(40) NOT NULL;

ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID;

UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE audit_logs
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';

ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_logs_result CHECK (result IN ('SUCCESS', 'FAILURE'));

CREATE INDEX idx_user_roles_role ON user_roles(role_key, user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_audit_logs_actor_time ON audit_logs(actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_action_time ON audit_logs(action, occurred_at DESC);

INSERT INTO permissions (permission_key, description) VALUES
    ('admin:access', 'Accéder au back-office'),
    ('users:read', 'Consulter les utilisateurs'),
    ('users:manage', 'Créer et modifier les utilisateurs internes'),
    ('profile:read:self', 'Consulter son propre profil client'),
    ('profile:update:self', 'Modifier son propre profil client');

INSERT INTO role_permissions (role_key, permission_key)
SELECT 'SUPER_ADMIN', permission_key FROM permissions;

INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('MANAGER', 'admin:access'),
    ('AGENT', 'admin:access'),
    ('CONCIERGE', 'admin:access'),
    ('ACCOUNTANT', 'admin:access'),
    ('CUSTOMER', 'profile:read:self'),
    ('CUSTOMER', 'profile:update:self');
