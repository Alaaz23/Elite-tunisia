DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM quotes) THEN
        RAISE EXCEPTION 'Legacy quotes require an approved booking mapping before V5';
    END IF;
END $$;
CREATE SEQUENCE quote_reference_seq;
ALTER TABLE quotes ADD COLUMN booking_id UUID NOT NULL REFERENCES bookings(id);
ALTER TABLE quotes ADD COLUMN reference VARCHAR(40) NOT NULL UNIQUE;
ALTER TABLE quotes ADD COLUMN version INTEGER NOT NULL CHECK (version > 0);
ALTER TABLE quotes ADD CONSTRAINT quotes_booking_version_unique UNIQUE (booking_id, version);
ALTER TABLE quotes ALTER COLUMN valid_until TYPE TIMESTAMPTZ USING valid_until::TIMESTAMPTZ;
ALTER TABLE quotes ALTER COLUMN valid_until SET NOT NULL;
ALTER TABLE quotes RENAME COLUMN total TO total_amount;
ALTER TABLE quotes ADD COLUMN discount_amount NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0);
ALTER TABLE quotes ADD COLUMN tax_amount NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0);
ALTER TABLE quotes ADD COLUMN currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$');
ALTER TABLE quotes ADD COLUMN starts_at TIMESTAMPTZ NOT NULL;
ALTER TABLE quotes ADD COLUMN ends_at TIMESTAMPTZ NOT NULL;
ALTER TABLE quotes ADD COLUMN vehicle_name VARCHAR(240) NOT NULL;
ALTER TABLE quotes ADD COLUMN pickup_location VARCHAR(300) NOT NULL;
ALTER TABLE quotes ADD COLUMN dropoff_location VARCHAR(300) NOT NULL;
ALTER TABLE quotes ADD COLUMN customer_note VARCHAR(2000);
ALTER TABLE quotes ADD COLUMN internal_note VARCHAR(2000);
ALTER TABLE quotes ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE quotes ADD COLUMN sent_at TIMESTAMPTZ;
ALTER TABLE quotes ADD COLUMN accepted_at TIMESTAMPTZ;
ALTER TABLE quotes ADD COLUMN rejected_at TIMESTAMPTZ;
ALTER TABLE quotes ADD CONSTRAINT quotes_status_check CHECK (status IN ('DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED','CANCELLED'));
ALTER TABLE quotes ADD CONSTRAINT quotes_totals_check CHECK (discount_amount <= subtotal AND total_amount = subtotal - discount_amount + tax_amount AND ends_at > starts_at);

ALTER TABLE quote_items RENAME TO quote_lines;
ALTER TABLE quote_lines ALTER COLUMN quantity TYPE INTEGER;
ALTER TABLE quote_lines ADD COLUMN type VARCHAR(40) NOT NULL;
ALTER TABLE quote_lines ADD COLUMN amount NUMERIC(12,3) NOT NULL;
ALTER TABLE quote_lines ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';
ALTER TABLE quote_lines ADD COLUMN position INTEGER NOT NULL CHECK (position >= 0);
ALTER TABLE quote_lines ADD CONSTRAINT quote_lines_position_unique UNIQUE (quote_id, position);
ALTER TABLE quote_lines ADD CONSTRAINT quote_lines_type_check CHECK (type IN ('VEHICLE_RENTAL','DRIVER','EXTRA_SERVICE','DISCOUNT','TAX'));
ALTER TABLE quote_lines ADD CONSTRAINT quote_lines_amount_check CHECK (amount = quantity * unit_price * CASE WHEN type = 'DISCOUNT' THEN -1 ELSE 1 END);

ALTER TABLE services ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'TND' CHECK (currency ~ '^[A-Z]{3}$');
CREATE TABLE quote_events (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES quotes(id),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(60) NOT NULL,
    message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX quotes_customer_created_idx ON quotes (customer_id, created_at DESC);
CREATE INDEX quotes_status_valid_until_idx ON quotes (status, valid_until);
CREATE INDEX quotes_created_idx ON quotes (created_at DESC);
CREATE INDEX quote_events_quote_time_idx ON quote_events (quote_id, created_at);
INSERT INTO permissions (permission_key, description) VALUES
    ('quotes:read','Consulter les devis autorisés'), ('quotes:create','Créer les devis agence'),
    ('quotes:manage','Modifier les brouillons et remises'), ('quotes:send','Envoyer les devis'),
    ('quotes:accept','Accepter ses devis'), ('quotes:reject','Refuser ses devis');
INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('SUPER_ADMIN','quotes:read'), ('SUPER_ADMIN','quotes:create'), ('SUPER_ADMIN','quotes:manage'), ('SUPER_ADMIN','quotes:send'),
    ('MANAGER','quotes:read'), ('MANAGER','quotes:create'), ('MANAGER','quotes:manage'), ('MANAGER','quotes:send'),
    ('AGENT','quotes:read'), ('AGENT','quotes:create'), ('AGENT','quotes:manage'), ('AGENT','quotes:send'),
    ('CONCIERGE','quotes:read'), ('ACCOUNTANT','quotes:read'),
    ('CUSTOMER','quotes:read'), ('CUSTOMER','quotes:accept'), ('CUSTOMER','quotes:reject');