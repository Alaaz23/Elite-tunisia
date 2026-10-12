-- Add operational driver fields: license, expiry, certified, notes
ALTER TABLE drivers ADD COLUMN license_number varchar(255);
ALTER TABLE drivers ADD COLUMN license_expiry timestamptz;
ALTER TABLE drivers ADD COLUMN certified boolean DEFAULT false NOT NULL;
ALTER TABLE drivers ADD COLUMN notes text;
