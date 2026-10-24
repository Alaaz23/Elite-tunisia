-- DISABLED: migrated to central database/migrations V1 or will be applied centrally if missing
-- Original filename: V4__add_driver_fields.sql
-- Content preserved for audit
-- Add operational driver fields: license, expiry, certified, notes
ALTER TABLE drivers ADD COLUMN license_number varchar(255);
ALTER TABLE drivers ADD COLUMN license_expiry timestamptz;
ALTER TABLE drivers ADD COLUMN certified boolean DEFAULT false NOT NULL;
ALTER TABLE drivers ADD COLUMN notes text;
