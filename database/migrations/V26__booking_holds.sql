-- V26 : holds PENDING expirables, statut EXPIRED, garantie obligatoire à la confirmation (lot 5).
-- Aucune migration existante n'est modifiée. Les réservations existantes conservent leurs données.
--
-- Pourquoi un trigger et non une contrainte EXCLUDE sur PENDING : un hold n'est bloquant que pendant sa durée de
-- validité. Une contrainte statique bloquerait à jamais les dates d'un hold échu. Le trigger évalue donc la validité
-- à l'instant de l'opération (clock_timestamp), après l'acquisition du verrou véhicule.
--
-- Ordre de verrouillage : Vehicle (FOR UPDATE) puis Booking, conforme à docs/architecture/booking-lock-order.md.
-- Toutes les créations, confirmations et expirations d'un même véhicule sont donc sérialisées.

ALTER TABLE bookings ADD COLUMN hold_expires_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN guarantee_id UUID;
ALTER TABLE bookings ADD CONSTRAINT fk_bookings_guarantee
    FOREIGN KEY (guarantee_id) REFERENCES vehicle_guarantees(id) ON DELETE RESTRICT;

-- Les demandes PENDING déjà existantes reçoivent une échéance de 30 minutes après leur création.
-- Les plus anciennes deviennent échues et seront expirées au prochain passage du worker. Comportement à documenter.
UPDATE bookings SET hold_expires_at = created_at + INTERVAL '30 minutes'
WHERE status = 'PENDING' AND hold_expires_at IS NULL;

ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK (status IN
    ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'IN_PROGRESS', 'COMPLETED', 'EXPIRED'));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_pending_hold CHECK (status <> 'PENDING' OR hold_expires_at IS NOT NULL);

CREATE INDEX idx_bookings_vehicle_blocking ON bookings(vehicle_id, starts_at, ends_at)
    WHERE status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS');
CREATE INDEX idx_bookings_pending_hold_expiry ON bookings(hold_expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_bookings_guarantee ON bookings(guarantee_id) WHERE guarantee_id IS NOT NULL;

CREATE FUNCTION guard_bookings_availability() RETURNS trigger AS $$
DECLARE
    now_ts TIMESTAMPTZ := clock_timestamp();
BEGIN
    -- Une demande créée sans échéance explicite reçoit le hold standard de 30 minutes.
    IF TG_OP = 'INSERT' AND NEW.status = 'PENDING' AND NEW.hold_expires_at IS NULL THEN
        NEW.hold_expires_at := now_ts + INTERVAL '30 minutes';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        -- EXPIRED est terminal.
        IF OLD.status = 'EXPIRED' AND NEW.status <> 'EXPIRED' THEN
            RAISE EXCEPTION 'réservation expirée : aucune transition autorisée' USING ERRCODE = 'restrict_violation';
        END IF;
        -- Expiration uniquement pour un hold PENDING réellement échu.
        IF NEW.status = 'EXPIRED' AND OLD.status <> 'EXPIRED'
           AND NOT (OLD.status = 'PENDING' AND OLD.hold_expires_at <= now_ts) THEN
            RAISE EXCEPTION 'expiration refusée : le hold n''est pas échu ou la réservation n''est pas en attente'
                USING ERRCODE = 'restrict_violation';
        END IF;
        -- Confirmation d'un hold PENDING : refusée si le hold est échu.
        IF NEW.status = 'CONFIRMED' AND OLD.status = 'PENDING' AND NEW.hold_expires_at <= now_ts THEN
            RAISE EXCEPTION 'hold expiré : confirmation refusée' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    -- Contrôle de chevauchement : seulement pour une écriture qui rend la réservation bloquante.
    IF (NEW.status IN ('CONFIRMED', 'IN_PROGRESS')
        OR (NEW.status = 'PENDING' AND NEW.hold_expires_at > now_ts))
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
              AND (b.status IN ('CONFIRMED', 'IN_PROGRESS')
                   OR (b.status = 'PENDING' AND b.hold_expires_at > now_ts))
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

CREATE TRIGGER trg_bookings_availability_guard
    BEFORE INSERT OR UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION guard_bookings_availability();
