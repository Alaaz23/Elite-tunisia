-- V12: Add driver operational fields if missing
DO $$
BEGIN
    -- Add columns only if drivers table exists and columns are missing
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'drivers'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'drivers' AND column_name = 'license_expiry'
        ) THEN
            ALTER TABLE drivers ADD COLUMN license_expiry timestamptz;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'drivers' AND column_name = 'certified'
        ) THEN
            ALTER TABLE drivers ADD COLUMN certified boolean DEFAULT false NOT NULL;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'drivers' AND column_name = 'notes'
        ) THEN
            ALTER TABLE drivers ADD COLUMN notes text;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'drivers' AND column_name = 'active'
        ) THEN
            ALTER TABLE drivers ADD COLUMN active boolean NOT NULL DEFAULT true;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'drivers' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE drivers ADD COLUMN created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP;
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'drivers' AND column_name = 'status'
        ) THEN
            ALTER TABLE drivers ALTER COLUMN status SET DEFAULT 'ACTIVE';
        END IF;
    END IF;
END;
$$;
