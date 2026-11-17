-- V21 (PROPOSITION, NON APPLIQUEE) : au plus un paiement CAPTURED par devis.
-- Index unique partiel : ne concerne que le statut CAPTURED ; PENDING, AUTHORIZED, FAILED,
-- REFUNDED et CANCELLED restent autorises en plusieurs exemplaires par devis.
-- Prerequis : aucun devis ne doit avoir plusieurs paiements CAPTURED (verifie en lecture seule le 2026-11-02 :
-- quotes_with_multiple_captured = 0). Sinon la creation de l'index echoue, ce qui est le comportement voulu.
-- Ce fichier est dans database/proposed/ pour ne pas etre copie dans db/migration par backend/pom.xml.
-- Le deplacer vers database/migrations/ n'est fait qu'apres autorisation explicite.

CREATE UNIQUE INDEX uq_payments_one_captured_per_quote
ON payments (quote_id)
WHERE status = 'CAPTURED';
