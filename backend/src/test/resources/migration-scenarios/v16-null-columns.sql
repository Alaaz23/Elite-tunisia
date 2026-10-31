-- État V16 : lignes écrites directement en SQL avec les trois colonnes NULL alignées par V17
-- (invoice_lines.metadata, payments.refunded_amount, quotes.vehicle_id).
INSERT INTO bookings (id, customer_id, vehicle_id, starts_at, ends_at, status, total_amount, currency, reference)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
        '55555555-5555-5555-5555-555555555555', '2030-03-10T10:00:00Z', '2030-03-12T10:00:00Z',
        'PENDING', 240.000, 'TND', 'ET-2030-000010');
INSERT INTO quotes (id, customer_id, vehicle_id, status, valid_until, subtotal, total_amount, booking_id, reference,
                    version, discount_amount, tax_amount, currency, starts_at, ends_at, vehicle_name,
                    pickup_location, dropoff_location)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', NULL, 'DRAFT',
        '2030-02-01T00:00:00Z', 240.000, 240.000, 'aaaaaaaa-0000-0000-0000-000000000001', 'Q-2030-000010',
        1, 0, 0, 'TND', '2030-03-10T10:00:00Z', '2030-03-12T10:00:00Z', 'Legacy Brand Legacy Model',
        'Tunis', 'Tunis');
INSERT INTO payments (id, quote_id, booking_id, customer_id, amount, currency, method, status, refunded_amount)
VALUES ('cccccccc-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001',
        'aaaaaaaa-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
        240.000, 'TND', 'CARD', 'CAPTURED', NULL);
INSERT INTO invoices (id, reference, booking_id, quote_id, customer_id, currency, subtotal, discount_amount,
                      tax_amount, total_amount, issued_at, status)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'INV-2030-000010', 'aaaaaaaa-0000-0000-0000-000000000001',
        'bbbbbbbb-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'TND',
        240.000, 0, 0, 240.000, '2030-03-12T10:00:00Z', 'ISSUED');
INSERT INTO invoice_lines (id, invoice_id, type, description, quantity, unit_price, amount, metadata, position)
VALUES ('eeeeeeee-0000-0000-0000-000000000002', 'dddddddd-0000-0000-0000-000000000001',
        'VEHICLE_RENTAL', 'Location', 1, 240.000, 240.000, NULL, 0);
