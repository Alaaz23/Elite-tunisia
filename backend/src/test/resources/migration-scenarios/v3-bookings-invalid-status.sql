-- État V3 : une réservation avec un statut libre hors de la liste imposée par V4.
INSERT INTO bookings (id, customer_id, vehicle_id, starts_at, ends_at, status, total_amount, currency) VALUES
 ('aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555',
  '2030-01-10T10:00:00Z', '2030-01-12T10:00:00Z', 'ACTIVE', 240.000, 'TND');
