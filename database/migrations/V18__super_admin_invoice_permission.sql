-- V18 : AdminInvoiceController exige invoices:read, mais V13 ne l'accordait qu'a CUSTOMER
-- (qui n'a pas admin:access). Sans cette migration, aucun compte ne pouvait consulter les
-- factures cote administration sur une base migree par Flyway ; le seeder H2 le masquait.
-- Les autres roles internes ne sont pas modifies : leur acces facture releve d'une decision metier.
INSERT INTO role_permissions (role_key, permission_key)
VALUES ('SUPER_ADMIN', 'invoices:read')
ON CONFLICT (role_key, permission_key) DO NOTHING;
