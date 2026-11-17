-- V23 : fondations partenaires (lot 2).
-- Préserve les données existantes : aucune affectation automatique, aucune suppression.
-- partners.commission_rate (V1) est conservé volontairement : sa suppression relève d'une décision D1 distincte.

ALTER TABLE partners
    ADD COLUMN address VARCHAR(255),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN tax_identifier VARCHAR(40),
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN deactivated_at TIMESTAMPTZ;

ALTER TABLE partners
    ADD CONSTRAINT ck_partners_type CHECK (partner_type IN ('INDIVIDUAL', 'COMPANY')),
    ADD CONSTRAINT ck_partners_name_not_blank CHECK (btrim(name) <> ''),
    ADD CONSTRAINT ck_partners_deactivated_consistent CHECK (
        (active = TRUE AND deactivated_at IS NULL) OR (active = FALSE)
    );

CREATE UNIQUE INDEX uq_partners_user_id ON partners(user_id) WHERE user_id IS NOT NULL;

ALTER TABLE vehicles
    ADD COLUMN partner_id UUID REFERENCES partners(id) ON DELETE RESTRICT;

CREATE INDEX idx_vehicles_partner_id ON vehicles(partner_id) WHERE partner_id IS NOT NULL;

CREATE TABLE vehicle_partner_assignments (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,
    reason VARCHAR(500) NOT NULL,
    assigned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_vpa_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_vpa_period CHECK (ended_at IS NULL OR ended_at >= assigned_at)
);

-- Un seul rattachement courant par véhicule.
CREATE UNIQUE INDEX uq_vpa_one_current_per_vehicle ON vehicle_partner_assignments(vehicle_id) WHERE ended_at IS NULL;
CREATE INDEX idx_vpa_vehicle_assigned_at ON vehicle_partner_assignments(vehicle_id, assigned_at);
CREATE INDEX idx_vpa_partner_id ON vehicle_partner_assignments(partner_id);

-- Historique append-only : aucune suppression, et seule la fermeture (ended_at NULL -> valeur) est autorisée en UPDATE.
CREATE FUNCTION guard_vehicle_partner_assignments() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'vehicle_partner_assignments est append-only' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.ended_at IS NOT NULL OR NEW.ended_at IS NULL
        OR NEW.id <> OLD.id OR NEW.vehicle_id <> OLD.vehicle_id OR NEW.partner_id <> OLD.partner_id
        OR NEW.assigned_at <> OLD.assigned_at OR NEW.reason <> OLD.reason
        OR NEW.assigned_by_user_id <> OLD.assigned_by_user_id THEN
        RAISE EXCEPTION 'modification interdite sur vehicle_partner_assignments' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicle_partner_assignments_guard
    BEFORE UPDATE OR DELETE ON vehicle_partner_assignments
    FOR EACH ROW EXECUTE FUNCTION guard_vehicle_partner_assignments();

INSERT INTO roles (role_key, display_name) VALUES
    ('PARTNER', 'Partenaire')
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO permissions (permission_key, description) VALUES
    ('partners:read', 'Consulter les partenaires et leurs véhicules'),
    ('partners:manage', 'Créer et gérer les partenaires et les affectations de véhicules'),
    ('partner-portal:read', 'Consulter son propre espace partenaire (lecture seule)')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('SUPER_ADMIN', 'partners:read'), ('SUPER_ADMIN', 'partners:manage'),
    ('MANAGER', 'partners:read'), ('MANAGER', 'partners:manage'),
    ('AGENT', 'partners:read'),
    ('PARTNER', 'partner-portal:read')
ON CONFLICT (role_key, permission_key) DO NOTHING;
