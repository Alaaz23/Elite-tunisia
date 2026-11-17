-- V25 : garanties véhicule-partenaire (lot 4).
-- Numérotée V25 : V24 est déjà utilisée par l'archivage des véhicules (lot 3).
-- partner_id identifie le partenaire ayant engagé le véhicule : il n'est jamais modifié, même si le véhicule change
-- de partenaire ensuite. Aucune clé composite vers vehicle_partner_assignments n'est créée, pour conserver l'historique.
-- Aucune donnée existante n'est modifiée.

CREATE TABLE vehicle_guarantees (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE RESTRICT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reason VARCHAR(500) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    withdrawn_at TIMESTAMPTZ,
    CONSTRAINT ck_vg_status CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_vg_period CHECK (ends_at > starts_at AND ends_at <= starts_at + INTERVAL '90 days'),
    CONSTRAINT ck_vg_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_vg_withdrawal_consistent CHECK (
        (status = 'ACTIVE' AND withdrawn_at IS NULL AND withdrawn_by_user_id IS NULL)
        OR (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL AND withdrawn_by_user_id IS NOT NULL)
    ),
    CONSTRAINT ck_vg_withdrawn_after_created CHECK (withdrawn_at IS NULL OR withdrawn_at >= created_at)
);

CREATE INDEX idx_vg_vehicle_period ON vehicle_guarantees(vehicle_id, starts_at, ends_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_vg_partner_starts ON vehicle_guarantees(partner_id, starts_at);

-- Création uniquement en statut ACTIVE. Le verrou sur le véhicule sérialise les créations concurrentes et les
-- transferts ; le contrôle de chevauchement porte sur les seules garanties ACTIVE.
CREATE FUNCTION guard_vehicle_guarantees_insert() RETURNS trigger AS $$
BEGIN
    IF NEW.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'une garantie doit être créée en statut ACTIVE' USING ERRCODE = 'restrict_violation';
    END IF;
    PERFORM 1 FROM vehicles WHERE id = NEW.vehicle_id FOR UPDATE;
    IF EXISTS (
        SELECT 1 FROM vehicle_guarantees g
        WHERE g.vehicle_id = NEW.vehicle_id
          AND g.status = 'ACTIVE'
          AND g.starts_at < NEW.ends_at
          AND g.ends_at > NEW.starts_at
    ) THEN
        RAISE EXCEPTION 'garantie chevauchante pour ce véhicule' USING ERRCODE = 'exclusion_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicle_guarantees_insert
    BEFORE INSERT ON vehicle_guarantees
    FOR EACH ROW EXECUTE FUNCTION guard_vehicle_guarantees_insert();

-- Historique conservé : suppression interdite ; seul le retrait (ACTIVE -> WITHDRAWN) est autorisé en UPDATE,
-- et aucun champ métier ni identité du partenaire engagé ne peut être réécrit.
CREATE FUNCTION guard_vehicle_guarantees_update() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'vehicle_guarantees est conservé : suppression interdite' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status <> 'ACTIVE' OR NEW.status <> 'WITHDRAWN'
        OR NEW.id <> OLD.id
        OR NEW.vehicle_id <> OLD.vehicle_id
        OR NEW.partner_id <> OLD.partner_id
        OR NEW.starts_at <> OLD.starts_at
        OR NEW.ends_at <> OLD.ends_at
        OR NEW.reason <> OLD.reason
        OR NEW.created_by_user_id <> OLD.created_by_user_id
        OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'modification interdite sur vehicle_guarantees' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vehicle_guarantees_update
    BEFORE UPDATE OR DELETE ON vehicle_guarantees
    FOR EACH ROW EXECUTE FUNCTION guard_vehicle_guarantees_update();
