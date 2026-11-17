-- V21 : au plus un paiement CAPTURED par devis.
-- Index unique partiel : ne concerne que le statut CAPTURED ; PENDING, AUTHORIZED, FAILED,
-- REFUNDED et CANCELLED restent autorises en plusieurs exemplaires par devis.
-- Echec attendu si des doublons CAPTURED existent deja (comportement voulu, pas de donnee modifiee).

CREATE UNIQUE INDEX uq_payments_one_captured_per_quote
    ON payments (quote_id)
    WHERE status = 'CAPTURED';
