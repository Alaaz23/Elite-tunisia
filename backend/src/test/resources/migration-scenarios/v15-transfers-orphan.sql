-- État V15 : table transfers d'Hibernate dont une ligne référence un chauffeur inexistant.
CREATE TABLE transfers (
    id uuid NOT NULL,
    dropoff_address varchar(255),
    driver_id uuid,
    passengers integer,
    pickup_address varchar(255),
    pickup_at timestamp with time zone,
    status varchar(255),
    vehicle_id uuid,
    PRIMARY KEY (id)
);
INSERT INTO transfers (id, pickup_address, dropoff_address, passengers, status, driver_id)
VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'Aeroport', 'Hotel', 2, 'PENDING', 'ffffffff-ffff-ffff-ffff-ffffffffffff');
