-- Add unique constraint to payments.idempotency_key to enforce cross-instance idempotency
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
    -- If running on H2 during tests, create unique index in H2-compatible syntax
    BEGIN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_idempotency_key ON payments (idempotency_key);
    EXCEPTION WHEN OTHERS THEN
        -- ignore if not supported in this environment
        RAISE NOTICE 'Could not create unique index for idempotency_key in this environment';
    END;
END;
$$;
