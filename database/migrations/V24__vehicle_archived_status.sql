-- V24 : statut ARCHIVED du catalogue véhicules (lot 3).
-- Élargit uniquement la contrainte de V3 : aucune donnée n'est modifiée.
-- ARCHIVED remplace la suppression physique d'un véhicule ayant un historique (V23 : partner_id, affectations).

ALTER TABLE vehicles
    DROP CONSTRAINT chk_vehicles_status,
    ADD CONSTRAINT chk_vehicles_status CHECK (
        status IN ('AVAILABLE', 'RESERVED', 'RENTED', 'MAINTENANCE', 'UNAVAILABLE', 'ARCHIVED')
    );
