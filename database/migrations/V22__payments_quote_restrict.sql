-- V22 : payments.quote_id passe de ON DELETE CASCADE a ON DELETE RESTRICT.
-- Un devis ayant des paiements ne peut plus etre supprime : la trace financiere est preservee.
-- Aucune donnee modifiee. DROP et ADD sont dans la meme transaction Flyway ; ADD ... NOT VALID
-- evite un scan de la table. Les lignes existantes ont deja ete validees par l'ancienne FK
-- sur la meme cle referencee ; seule la regle de suppression change.

SET LOCAL lock_timeout = '5s';

ALTER TABLE payments DROP CONSTRAINT fk_payments_quote;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_quote
    FOREIGN KEY (quote_id) REFERENCES quotes(id) ON DELETE RESTRICT NOT VALID;
