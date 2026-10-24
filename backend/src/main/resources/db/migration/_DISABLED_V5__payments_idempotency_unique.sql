-- DISABLED: redundant with central database/migrations V6__payments.sql
-- Original filename: V5__payments_idempotency_unique.sql
-- Content preserved for audit
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        WHERE t.relname = 'payments' AND c.conname = 'uk_payments_idempotency_key'
    ) THEN
        ALTER TABLE payments ADD CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key);
    END IF;
EXCEPTION WHEN undefined_table THEN
    BEGIN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_idempotency_key ON payments (idempotency_key);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Could not create unique index for idempotency_key in this environment';
    END;
END;
$$;
