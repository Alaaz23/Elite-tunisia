-- V22 a recréé fk_payments_quote en NOT VALID et aucune migration ne l'a validée.
-- VALIDATE CONSTRAINT ne modifie aucune donnée : il échoue si une ligne orpheline existe.
ALTER TABLE payments VALIDATE CONSTRAINT fk_payments_quote;
