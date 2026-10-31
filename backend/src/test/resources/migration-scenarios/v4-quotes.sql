-- État V4 : un devis historique au format V1 (V5 refuse volontairement de migrer tant qu'il existe).
INSERT INTO quotes (id, customer_id, vehicle_id, status, valid_until, subtotal, total)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
        '55555555-5555-5555-5555-555555555555', 'DRAFT', '2030-02-01', 100.000, 100.000);
