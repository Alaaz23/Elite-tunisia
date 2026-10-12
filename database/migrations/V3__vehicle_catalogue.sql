ALTER TABLE brands
    ADD COLUMN description TEXT NOT NULL DEFAULT '',
    ADD COLUMN logo_url VARCHAR(1000),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE categories
    ADD COLUMN description TEXT NOT NULL DEFAULT '',
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE vehicles
    ALTER COLUMN model_year TYPE INTEGER,
    ALTER COLUMN passengers TYPE INTEGER,
    ALTER COLUMN doors TYPE INTEGER;

ALTER TABLE vehicles
    ADD COLUMN price_per_week NUMERIC(12, 3) NOT NULL,
    ADD COLUMN currency VARCHAR(3) NOT NULL,
    ADD COLUMN with_driver_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_vehicles_daily_price_positive CHECK (price_per_day > 0),
    ADD CONSTRAINT chk_vehicles_weekly_price_positive CHECK (price_per_week > 0),
    ADD CONSTRAINT chk_vehicles_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT chk_vehicles_year_range CHECK (model_year BETWEEN 1886 AND 2100),
    ADD CONSTRAINT chk_vehicles_status CHECK (status IN ('AVAILABLE', 'RESERVED', 'RENTED', 'MAINTENANCE', 'UNAVAILABLE')),
    ADD CONSTRAINT chk_vehicles_transmission CHECK (transmission IN ('AUTOMATIC', 'MANUAL', 'SEMI_AUTOMATIC')),
    ADD CONSTRAINT chk_vehicles_fuel CHECK (fuel IN ('PETROL', 'DIESEL', 'HYBRID', 'ELECTRIC', 'PLUG_IN_HYBRID')),
    ADD CONSTRAINT chk_vehicles_driver_price CHECK (NOT with_driver_available OR (price_with_driver IS NOT NULL AND price_with_driver > 0));

ALTER TABLE vehicle_images
    RENAME COLUMN storage_key TO url;

ALTER TABLE vehicle_images
    RENAME COLUMN display_order TO position;

ALTER TABLE vehicle_images
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

WITH ranked_images AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY vehicle_id ORDER BY position, id) AS row_number
    FROM vehicle_images
)
UPDATE vehicle_images AS image
SET is_primary = TRUE
FROM ranked_images
WHERE image.id = ranked_images.id AND ranked_images.row_number = 1;

CREATE UNIQUE INDEX idx_vehicle_images_single_primary
    ON vehicle_images(vehicle_id) WHERE is_primary;
CREATE INDEX idx_vehicle_images_vehicle_position
    ON vehicle_images(vehicle_id, position, id);

ALTER TABLE vehicle_features
    ADD COLUMN id UUID;

UPDATE vehicle_features SET id = gen_random_uuid() WHERE id IS NULL;

ALTER TABLE vehicle_features
    ALTER COLUMN id SET NOT NULL,
    ADD COLUMN position SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    DROP CONSTRAINT vehicle_features_pkey,
    ADD CONSTRAINT vehicle_features_pkey PRIMARY KEY (id),
    ADD CONSTRAINT uq_vehicle_features_name UNIQUE (vehicle_id, feature);

CREATE INDEX idx_vehicle_features_vehicle_position
    ON vehicle_features(vehicle_id, position, feature);
CREATE INDEX idx_vehicles_catalogue_status ON vehicles(status, created_at DESC);
CREATE INDEX idx_vehicles_brand_category ON vehicles(brand_id, category_id);

INSERT INTO permissions (permission_key, description) VALUES
    ('vehicles:read', 'Consulter le catalogue de véhicules'),
    ('vehicles:manage', 'Créer et gérer les véhicules'),
    ('brands:read', 'Consulter les marques'),
    ('brands:manage', 'Créer et gérer les marques'),
    ('categories:read', 'Consulter les catégories'),
    ('categories:manage', 'Créer et gérer les catégories');

INSERT INTO role_permissions (role_key, permission_key) VALUES
    ('SUPER_ADMIN', 'vehicles:read'),
    ('SUPER_ADMIN', 'vehicles:manage'),
    ('SUPER_ADMIN', 'brands:read'),
    ('SUPER_ADMIN', 'brands:manage'),
    ('SUPER_ADMIN', 'categories:read'),
    ('SUPER_ADMIN', 'categories:manage'),
    ('MANAGER', 'vehicles:read'),
    ('MANAGER', 'vehicles:manage'),
    ('MANAGER', 'brands:read'),
    ('MANAGER', 'brands:manage'),
    ('MANAGER', 'categories:read'),
    ('MANAGER', 'categories:manage'),
    ('AGENT', 'vehicles:read'),
    ('AGENT', 'brands:read'),
    ('AGENT', 'categories:read'),
    ('CONCIERGE', 'vehicles:read'),
    ('CONCIERGE', 'brands:read'),
    ('CONCIERGE', 'categories:read'),
    ('ACCOUNTANT', 'vehicles:read'),
    ('ACCOUNTANT', 'brands:read'),
    ('ACCOUNTANT', 'categories:read');
