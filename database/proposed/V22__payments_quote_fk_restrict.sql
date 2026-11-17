-- PROPOSITION NON APPLIQUEE - ne pas placer dans database/migrations sans autorisation.
-- V22 (proposition, revision 2) : payments.quote_id passe de ON DELETE CASCADE a ON DELETE RESTRICT.
-- Independante de V21 (index unique partiel CAPTURED) : les deux peuvent etre appliquees dans n'importe quel ordre.
-- Aucune donnee modifiee : la FK existe deja, on change seulement la regle de suppression.
--
-- Remplacement atomique : DROP et ADD dans la meme transaction (Flyway execute chaque fichier dans une transaction).
-- Aucune session ne voit de moment ou la FK est absente ou ou les deux FK coexistent.
-- Pas de scan : ADD ... NOT VALID ne lit pas la table. La contrainte est appliquee aux nouvelles
-- insertions, mises a jour et suppressions de devis des la fin de la transaction.
-- SET LOCAL lock_timeout : si un verrou ne peut pas etre obtenu, la migration echoue sans attendre indefiniment
-- (une attente en file bloquerait toutes les requetes sur payments).
--
-- VALIDATE CONSTRAINT n'est PAS dans ce fichier : il scanne payments et doit s'executer hors de cette transaction,
-- sinon le verrou ACCESS EXCLUSIVE du DROP serait conserve pendant tout le scan. Voir la revue, section 10.
--
-- Verification apres cette migration :
--   SELECT confdeltype, convalidated FROM pg_constraint WHERE conname = 'fk_payments_quote';
--   attendu : 'r' et false (false tant que VALIDATE n'a pas ete execute).

SET LOCAL lock_timeout = '5s';

ALTER TABLE payments DROP CONSTRAINT fk_payments_quote;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_quote
    FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID;
