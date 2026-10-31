-- État V5 : une réservation, un paiement et une facture au format V1 (V6/V7 les renomment en legacy_*).
INSERT INTO bookings (id, customer_id, vehicle_id, starts_at, ends_at, status, total_amount, currency, reference)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
        '55555555-5555-5555-5555-555555555555', '2030-01-10T10:00:00Z', '2030-01-12T10:00:00Z',
        'CONFIRMED', 240.000, 'TND', 'ET-2030-000001');
INSERT INTO payments (id, booking_id, amount, currency, status, provider, provider_reference)
VALUES ('cccccccc-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 240.000, 'TND',
        'CAPTURED', 'legacy-provider', 'ref-1');
INSERT INTO invoices (id, booking_id, invoice_number, status, total, issued_at)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'LEGACY-INV-1',
        'ISSUED', 240.000, '2030-01-12T10:00:00Z');
