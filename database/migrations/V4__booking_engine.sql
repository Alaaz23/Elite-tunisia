CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE SEQUENCE booking_reference_seq;
ALTER TABLE bookings ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE bookings ADD COLUMN reference VARCHAR(40);
UPDATE bookings SET reference = 'ET-' || EXTRACT(YEAR FROM created_at)::INTEGER || '-' || LPAD(nextval('booking_reference_seq')::TEXT, 6, '0');
ALTER TABLE bookings ALTER COLUMN reference SET NOT NULL;
ALTER TABLE bookings ADD CONSTRAINT bookings_reference_unique UNIQUE (reference);
ALTER TABLE bookings ADD COLUMN pickup_location VARCHAR(300) NOT NULL DEFAULT '';
ALTER TABLE bookings ADD COLUMN dropoff_location VARCHAR(300) NOT NULL DEFAULT '';
ALTER TABLE bookings ADD COLUMN base_price NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN additional_charges NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN discount NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN tax NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN number_of_days INTEGER NOT NULL DEFAULT 1;
ALTER TABLE bookings ADD COLUMN daily_rate NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN customer_note VARCHAR(2000);
ALTER TABLE bookings ADD COLUMN internal_note VARCHAR(2000);
ALTER TABLE bookings ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE bookings ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMPTZ;
UPDATE bookings SET base_price = total_amount;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check CHECK (status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED','IN_PROGRESS','COMPLETED'));
ALTER TABLE bookings ADD CONSTRAINT bookings_amounts_check CHECK (base_price >= 0 AND additional_charges >= 0 AND discount >= 0 AND tax >= 0 AND number_of_days > 0 AND daily_rate >= 0);
ALTER TABLE bookings ADD CONSTRAINT bookings_no_blocking_overlap EXCLUDE USING gist (
    vehicle_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status IN ('CONFIRMED', 'IN_PROGRESS'));

CREATE INDEX bookings_status_starts_idx ON bookings (status, starts_at);

CREATE TABLE booking_events (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(60) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX booking_events_booking_time_idx ON booking_events (booking_id, created_at);

INSERT INTO permissions (permission_key, description) VALUES
    ('bookings:read', 'Consulter les réservations autorisées'),
    ('bookings:create', 'Créer ses demandes de réservation'),
    ('bookings:manage', 'Gérer les réservations agence'),
    ('bookings:cancel', 'Annuler les réservations autorisées');
INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('SUPER_ADMIN','bookings:read'), ('SUPER_ADMIN','bookings:create'), ('SUPER_ADMIN','bookings:manage'), ('SUPER_ADMIN','bookings:cancel'),
    ('MANAGER','bookings:read'), ('MANAGER','bookings:manage'), ('MANAGER','bookings:cancel'),
    ('AGENT','bookings:read'), ('AGENT','bookings:manage'), ('AGENT','bookings:cancel'),
    ('CONCIERGE','bookings:read'), ('ACCOUNTANT','bookings:read'),
    ('CUSTOMER','bookings:read'), ('CUSTOMER','bookings:create'), ('CUSTOMER','bookings:cancel');