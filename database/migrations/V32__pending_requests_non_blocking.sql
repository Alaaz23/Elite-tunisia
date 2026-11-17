-- V32 : les demandes PENDING ne bloquent plus le véhicule et n'expirent plus automatiquement.
-- Décision métier validée : une demande PENDING ne bloque jamais la disponibilité ; plusieurs demandes PENDING
-- incompatibles peuvent coexister ; seule la confirmation (passage à CONFIRMED) est soumise au contrôle définitif.
-- Aucune migration antérieure n'est modifiée (V4 et V26 restent en l'état). Aucune donnée n'est supprimée ni réécrite :
-- hold_expires_at des lignes existantes est conservé tel quel, à titre informatif, et n'a plus d'effet métier.
--
-- Contrôle de chevauchement : uniquement CONFIRMED et IN_PROGRESS, après acquisition du verrou véhicule (FOR UPDATE),
-- conformément à docs/architecture/booking-lock-order.md. La contrainte EXCLUDE de V4 reste le filet de sécurité.

ALTER TABLE bookings DROP CONSTRAINT ck_bookings_pending_hold;

DROP INDEX idx_bookings_vehicle_blocking;
CREATE INDEX idx_bookings_vehicle_blocking ON bookings(vehicle_id, starts_at, ends_at)
    WHERE status IN ('CONFIRMED', 'IN_PROGRESS');
DROP INDEX idx_bookings_pending_hold_expiry;

CREATE OR REPLACE FUNCTION guard_bookings_availability() RETURNS trigger AS $$
DECLARE
    now_ts TIMESTAMPTZ := clock_timestamp();
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- EXPIRED est terminal (statut historique, plus produit automatiquement).
        IF OLD.status = 'EXPIRED' AND NEW.status <> 'EXPIRED' THEN
            RAISE EXCEPTION 'réservation expirée : aucune transition autorisée' USING ERRCODE = 'restrict_violation';
        END IF;
        -- Aucune expiration automatique : toute nouvelle transition vers EXPIRED est refusée.
        IF NEW.status = 'EXPIRED' AND OLD.status <> 'EXPIRED' THEN
            RAISE EXCEPTION 'expiration refusée : les demandes PENDING n''expirent pas automatiquement'
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    -- Contrôle de chevauchement : seulement pour une écriture qui rend la réservation bloquante.
    IF NEW.status IN ('CONFIRMED', 'IN_PROGRESS')
       AND (TG_OP = 'INSERT' OR NEW.status IS DISTINCT FROM OLD.status
            OR NEW.vehicle_id IS DISTINCT FROM OLD.vehicle_id
            OR NEW.starts_at IS DISTINCT FROM OLD.starts_at
            OR NEW.ends_at IS DISTINCT FROM OLD.ends_at) THEN
        PERFORM 1 FROM vehicles WHERE id = NEW.vehicle_id FOR UPDATE;
        IF EXISTS (
            SELECT 1 FROM bookings b
            WHERE b.vehicle_id = NEW.vehicle_id
              AND b.id <> NEW.id
              AND b.starts_at < NEW.ends_at
              AND b.ends_at > NEW.starts_at
              AND b.status IN ('CONFIRMED', 'IN_PROGRESS')
        ) THEN
            RAISE EXCEPTION 'période déjà bloquée pour ce véhicule' USING ERRCODE = 'exclusion_violation';
        END IF;
    END IF;

    -- Confirmation : une garantie ACTIVE du même véhicule doit couvrir intégralement la période.
    IF NEW.status = 'CONFIRMED' AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM 'CONFIRMED') THEN
        IF NEW.guarantee_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM vehicle_guarantees g
            WHERE g.id = NEW.guarantee_id
              AND g.vehicle_id = NEW.vehicle_id
              AND g.status = 'ACTIVE'
              AND g.starts_at <= NEW.starts_at
              AND g.ends_at >= NEW.ends_at
        ) THEN
            RAISE EXCEPTION 'aucune garantie active ne couvre la réservation' USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
