-- V29 : une seule facture principale par réservation (un devis par version, plusieurs versions possibles).
-- V28 ne couvre que quote_id : deux versions acceptées d'une même réservation pourraient produire deux factures principales.
-- Les factures complémentaires (frais de retour) ont quote_id NULL et restent autorisées.
-- Aucune donnée n'est supprimée. Pré-contrôle à exécuter AVANT migration sur la base cible :
--   SELECT booking_id, count(*) FROM invoices WHERE quote_id IS NOT NULL GROUP BY booking_id HAVING count(*) > 1;
-- Cette requête doit renvoyer 0 ligne ; sinon la migration échoue volontairement et doit être arbitrée métier.
CREATE UNIQUE INDEX uq_invoices_one_main_per_booking ON invoices (booking_id) WHERE quote_id IS NOT NULL;
