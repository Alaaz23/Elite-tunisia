-- TransferEntity était mappée sans migration : Hibernate "validate" échouait sur PostgreSQL.
CREATE TABLE transfers (
    id uuid PRIMARY KEY,
    pickup_at timestamptz,
    pickup_address varchar(255),
    dropoff_address varchar(255),
    passengers integer,
    driver_id uuid REFERENCES drivers(id) ON DELETE SET NULL,
    vehicle_id uuid REFERENCES vehicles(id) ON DELETE SET NULL,
    status varchar(255) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_transfers_driver ON transfers(driver_id);
CREATE INDEX idx_transfers_vehicle ON transfers(vehicle_id);
CREATE INDEX idx_transfers_pickup_at ON transfers(pickup_at);

-- VehicleImageEntity autorise 1000 caractères ; V3 n'en autorisait que 500.
ALTER TABLE vehicle_images ALTER COLUMN url TYPE varchar(1000);
