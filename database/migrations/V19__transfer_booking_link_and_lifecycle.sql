-- V19 : rattache les transferts à une réservation et fiabilise leur cycle de vie.
-- Ne réécrit pas V16 (déjà appliquée) : elle complète la table transfers.
--
-- booking_id reste nullable : d'éventuelles lignes antérieures (créées sans réservation) ne sont pas
-- modifiées ni supprimées ; l'API les traite en lecture seule (hors annulation).
ALTER TABLE transfers ADD COLUMN booking_id uuid;
ALTER TABLE transfers ADD COLUMN created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE transfers ADD COLUMN updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE transfers ADD CONSTRAINT fk_transfers_booking
    FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE RESTRICT;

-- NOT VALID : appliquée aux nouvelles écritures sans échouer sur d'éventuelles lignes à statut libre.
-- Après nettoyage des données : ALTER TABLE transfers VALIDATE CONSTRAINT transfers_status_check;
ALTER TABLE transfers ADD CONSTRAINT transfers_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')) NOT VALID;
ALTER TABLE transfers ADD CONSTRAINT transfers_passengers_check
    CHECK (passengers IS NULL OR passengers > 0) NOT VALID;

CREATE INDEX idx_transfers_booking ON transfers(booking_id);
CREATE INDEX idx_transfers_driver_pickup ON transfers(driver_id, pickup_at);
