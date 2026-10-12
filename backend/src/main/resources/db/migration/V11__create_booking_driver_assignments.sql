-- Create booking_driver_assignments table (non-destructive)
CREATE TABLE IF NOT EXISTS booking_driver_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL,
  driver_id uuid NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  CONSTRAINT chk_bda_start_end CHECK (starts_at < ends_at)
);

-- Foreign keys (non-blocking if referenced tables missing at migration time will fail; keep standard FK)
ALTER TABLE IF EXISTS booking_driver_assignments
  ADD CONSTRAINT fk_bda_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE;
ALTER TABLE IF EXISTS booking_driver_assignments
  ADD CONSTRAINT fk_bda_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_bda_driver ON booking_driver_assignments(driver_id);
CREATE INDEX IF NOT EXISTS idx_bda_booking ON booking_driver_assignments(booking_id);
