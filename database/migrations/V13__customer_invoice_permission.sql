INSERT INTO permissions (permission_key, description)
VALUES ('invoices:read', 'Consulter ses factures');

INSERT INTO role_permissions (role_key, permission_key)
VALUES ('CUSTOMER', 'invoices:read');
