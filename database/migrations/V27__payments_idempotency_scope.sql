-- Lot A (ISS-03) : l'idempotence des paiements est délimitée par client et devis.
-- Remplace l'unicité globale de la clé (V6). Aucune donnée n'est modifiée ni supprimée.
DROP INDEX IF EXISTS payments_idempotency_idx;

CREATE UNIQUE INDEX uq_payments_customer_quote_idempotency
    ON payments (customer_id, quote_id, idempotency_key);
