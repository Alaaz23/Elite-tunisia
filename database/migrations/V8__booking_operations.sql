CREATE TABLE booking_operations (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id),
    actor_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(40) NOT NULL,
    odometer_start INTEGER,
    odometer_end INTEGER,
    fuel_start INTEGER,
    fuel_end INTEGER,
    damages TEXT,
    extra_charges NUMERIC(12, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_booking_operations_booking_created ON booking_operations(booking_id, created_at);