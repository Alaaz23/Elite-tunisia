-- Pré-contrôle en lecture seule avant V29 (uq_invoices_one_main_per_booking).
-- À exécuter UNIQUEMENT sur une base temporaire de test autorisée (127.0.0.1:15432), jamais sur une base existante.
-- Aucune écriture. Chaque requête doit renvoyer 0 ligne pour que V29 puisse être appliquée sans arbitrage.

-- 1. Doublons de factures principales (quote_id renseigné) par réservation : bloquant.
SELECT booking_id, count(*) AS main_invoices
FROM invoices
WHERE quote_id IS NOT NULL
GROUP BY booking_id
HAVING count(*) > 1;

-- 2. Factures principales sans réservation : incohérence à arbitrer.
SELECT id, quote_id
FROM invoices
WHERE quote_id IS NOT NULL AND booking_id IS NULL;

-- 3. Factures principales dont le devis ne correspond pas à la réservation de la facture : incohérence à arbitrer.
SELECT i.id, i.booking_id, i.quote_id
FROM invoices i
JOIN quotes q ON q.id = i.quote_id
WHERE i.quote_id IS NOT NULL AND q.booking_id <> i.booking_id;

-- 4. Répartition informative : principales vs complémentaires (quote_id NULL), non bloquant.
SELECT (quote_id IS NOT NULL) AS is_main, count(*) AS invoices
FROM invoices
GROUP BY (quote_id IS NOT NULL);
