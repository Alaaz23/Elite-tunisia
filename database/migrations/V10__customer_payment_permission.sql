INSERT INTO permissions (permission_key, description)
VALUES ('payments:create', 'Payer ses devis acceptes');

INSERT INTO role_permissions (role_key, permission_key)
VALUES ('CUSTOMER', 'payments:create');