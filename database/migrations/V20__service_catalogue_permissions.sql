-- V20 : droits du catalogue de services additionnels (utilisé par les devis).
-- ON CONFLICT DO NOTHING : sans effet si une base a déjà ces clés.
-- Même répartition que le catalogue de véhicules (V3) : lecture pour le personnel, gestion pour SUPER_ADMIN et MANAGER.
INSERT INTO permissions (permission_key, description) VALUES
    ('services:read', 'Consulter le catalogue de services'),
    ('services:manage', 'Créer et gérer les services')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('SUPER_ADMIN', 'services:read'), ('SUPER_ADMIN', 'services:manage'),
    ('MANAGER', 'services:read'), ('MANAGER', 'services:manage'),
    ('AGENT', 'services:read'), ('CONCIERGE', 'services:read'), ('ACCOUNTANT', 'services:read')
ON CONFLICT (role_key, permission_key) DO NOTHING;
