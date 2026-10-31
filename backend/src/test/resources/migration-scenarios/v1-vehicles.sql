-- État V1 : un véhicule valide (V3 ajoute price_per_week et currency NOT NULL sans valeur par défaut).
INSERT INTO brands (id, name, slug) VALUES ('33333333-3333-3333-3333-333333333333', 'Legacy Brand', 'legacy-brand');
INSERT INTO categories (id, name, slug) VALUES ('44444444-4444-4444-4444-444444444444', 'Legacy Category', 'legacy-category');
INSERT INTO vehicles (id, brand_id, category_id, model, model_year, price_per_day, passengers, doors,
                      transmission, fuel, description, status)
VALUES ('55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444', 'Legacy Model', 2022, 120.000, 5, 4,
        'AUTOMATIC', 'PETROL', 'legacy vehicle', 'AVAILABLE');
