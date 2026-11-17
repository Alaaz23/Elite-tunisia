-- V28 : une seule facture principale par devis.
-- Les factures complémentaires (frais de retour) ont quote_id NULL et ne sont pas concernées.
-- Aucune donnée n'est supprimée. Pré-contrôle à exécuter AVANT migration sur la base cible :
--   SELECT quote_id, count(*) FROM invoices WHERE quote_id IS NOT NULL GROUP BY quote_id HAVING count(*) > 1;
-- Cette requête doit renvoyer 0 ligne ; sinon la migration échoue volontairement et doit être arbitrée métier.
CREATE UNIQUE INDEX uq_invoices_one_main_per_quote ON invoices (quote_id) WHERE quote_id IS NOT NULL;
