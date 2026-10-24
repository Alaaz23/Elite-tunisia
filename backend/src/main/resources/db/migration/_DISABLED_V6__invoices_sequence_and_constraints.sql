-- DISABLED: redundant with central database/migrations V7__invoices.sql
-- Original filename: V6__invoices_sequence_and_constraints.sql
-- Content preserved for audit
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relkind = 'S' AND relname = 'invoice_seq') THEN
        CREATE SEQUENCE invoice_seq START 1;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid WHERE t.relname = 'invoices' AND c.conname = 'uk_invoices_reference'
    ) THEN
        ALTER TABLE invoices ADD CONSTRAINT uk_invoices_reference UNIQUE (reference);
    END IF;
EXCEPTION WHEN undefined_table THEN
    BEGIN
        CREATE SEQUENCE IF NOT EXISTS invoice_seq START WITH 1;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Could not create invoice_seq in this environment';
    END;
    BEGIN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_reference ON invoices(reference);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Could not create unique index on invoices(reference) in this environment';
    END;
END;
$$;

CREATE TABLE IF NOT EXISTS invoice_payments (
  invoice_id uuid NOT NULL,
  payment_id uuid NOT NULL,
  PRIMARY KEY (invoice_id, payment_id)
);
