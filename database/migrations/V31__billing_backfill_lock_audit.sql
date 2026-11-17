-- V31 : durcissement de la rétrofacturation (Lot A.4).
-- 1. Accorde 'billing:backfill' au seul rôle SUPER_ADMIN. V30 a créé la permission sans aucune attribution ;
--    l'attribution à d'autres administrateurs relève d'une décision métier explicite et n'est pas faite ici.
-- 2. Ajoute un heartbeat au verrou : une exécution interrompue n'est reprise qu'après expiration du heartbeat.
-- 3. Trace chaque exécution (propriétaire, heartbeat, fin, reprise d'un verrou expiré).
-- Aucune donnée existante n'est modifiée ; V30 n'est pas modifiée.

INSERT INTO role_permissions (role_key, permission_key)
VALUES ('SUPER_ADMIN', 'billing:backfill')
ON CONFLICT (role_key, permission_key) DO NOTHING;

ALTER TABLE billing_backfill_lock ADD COLUMN heartbeat_at timestamptz;

CREATE TABLE billing_backfill_runs (
    run_id              uuid PRIMARY KEY,
    actor_id            uuid NOT NULL REFERENCES users(id),
    status              varchar(20) NOT NULL,
    started_at          timestamptz NOT NULL,
    heartbeat_at        timestamptz NOT NULL,
    finished_at         timestamptz,
    took_over_run_id    uuid,
    error_message       varchar(500),
    CONSTRAINT ck_billing_backfill_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'ABORTED', 'TAKEN_OVER')),
    CONSTRAINT ck_billing_backfill_runs_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL))
);

CREATE INDEX ix_billing_backfill_runs_status ON billing_backfill_runs (status, started_at);
