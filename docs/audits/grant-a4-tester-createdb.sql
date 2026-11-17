-- Script ADMINISTRATEUR, à exécuter uniquement par un superutilisateur sur le cluster de test 127.0.0.1:15432.
-- Objet : créer un rôle dédié aux tests de la classe PaymentBookingPostgresTest, PaymentQuotePostgresA4Test
-- et PartnerMigrationPostgresTest, avec le seul privilège CREATEDB.
-- Ne modifie aucune base existante. Ne touche pas au rôle elite_test. Ne donne ni SUPERUSER ni CREATEROLE.
--
-- Prérequis : le mot de passe doit être fourni hors du dépôt (variable psql :'pw', jamais écrit dans ce fichier).
-- Exécution : psql -h 127.0.0.1 -p 15432 -U postgres -d postgres -v pw="$PW" -f grant-a4-tester-createdb.sql
-- Retour arrière : DROP ROLE elite_a4_tester; (après suppression des bases et_a4_it_* éventuelles, si le rôle en est propriétaire)

CREATE ROLE elite_a4_tester LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD :'pw';
ALTER ROLE elite_a4_tester CREATEDB;

-- Vérification attendue : rolcreatedb = true, rolsuper = false, rolcreaterole = false.
SELECT rolname, rolcreatedb, rolsuper, rolcreaterole FROM pg_roles WHERE rolname = 'elite_a4_tester';
