-- Création du PREMIER compte SUPER_ADMIN, une seule fois, sur une base dont Flyway a déjà appliqué les migrations.
-- Aucun endpoint HTTP ne permet cette opération : c'est volontaire.
--
-- Secrets lus depuis l'environnement du client psql (jamais en argument de ligne de commande) :
--   ELITE_ADMIN_EMAIL    adresse e-mail du compte
--   ELITE_ADMIN_BCRYPT   hash bcrypt du mot de passe ($2a$/$2b$/$2y$, coût 10 ou plus)
--
-- Le script refuse de s'exécuter si un SUPER_ADMIN existe déjà ou si le hash n'a pas le format bcrypt.
-- Procédure complète : docs/first-admin.md
\set ON_ERROR_STOP on
\getenv admin_email ELITE_ADMIN_EMAIL
\getenv admin_hash ELITE_ADMIN_BCRYPT

SELECT (:'admin_email' <> '' AND position('@' IN :'admin_email') > 1) AS email_ok,
       (:'admin_hash' ~ '^\$2[aby]\$[0-9]{2}\$.{53}$') AS hash_ok,
       EXISTS (SELECT 1 FROM user_roles WHERE role_key = 'SUPER_ADMIN') AS has_admin \gset

\if :has_admin
  \echo 'REFUS : un SUPER_ADMIN existe déjà. Aucune modification.'
  DO $$ BEGIN RAISE EXCEPTION 'refus'; END $$;
\endif
\if :email_ok
\else
  \echo 'REFUS : ELITE_ADMIN_EMAIL absent ou invalide.'
  DO $$ BEGIN RAISE EXCEPTION 'refus'; END $$;
\endif
\if :hash_ok
\else
  \echo 'REFUS : ELITE_ADMIN_BCRYPT absent ou ce n''est pas un hash bcrypt.'
  DO $$ BEGIN RAISE EXCEPTION 'refus'; END $$;
\endif

BEGIN;
WITH u AS (
  INSERT INTO users (id, email, password_hash, enabled)
  VALUES (gen_random_uuid(), lower(trim(:'admin_email')), :'admin_hash', true) RETURNING id
), r AS (
  INSERT INTO user_roles (user_id, role_key) SELECT id, 'SUPER_ADMIN' FROM u
)
INSERT INTO employees (id, employee_number, job_title, first_name, last_name, phone)
SELECT id, 'EMP-ADMIN-' || upper(substr(replace(id::text, '-', ''), 1, 8)), 'Super administrateur', 'Super', 'Admin', '+216 00 000 000' FROM u;
COMMIT;
\echo 'Compte SUPER_ADMIN créé. Changez le mot de passe dès la première connexion.'
