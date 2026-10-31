-- Base commune pour les états V3 et suivants : un client, une marque, une catégorie et un véhicule valides.
INSERT INTO users (id, email, password_hash)
VALUES ('22222222-2222-2222-2222-222222222222', 'legacy.customer@example.test', 'not-a-real-hash');
INSERT INTO user_roles (user_id, role_key) VALUES ('22222222-2222-2222-2222-222222222222', 'CUSTOMER');
INSERT INTO customers (id, first_name, last_name, phone)
VALUES ('22222222-2222-2222-2222-222222222222', 'Legacy', 'Customer', '+216 20 000 000');
INSERT INTO brands (id, name, slug) VALUES ('33333333-3333-3333-3333-333333333333', 'Legacy Brand', 'legacy-brand');
INSERT INTO categories (id, name, slug) VALUES ('44444444-4444-4444-4444-444444444444', 'Legacy Category', 'legacy-category');
INSERT INTO vehicles (id, brand_id, category_id, model, model_year, price_per_day, passengers, doors,
                      transmission, fuel, description, status, price_per_week, currency)
VALUES ('55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444', 'Legacy Model', 2022, 120.000, 5, 4,
        'AUTOMATIC', 'PETROL', 'legacy vehicle', 'AVAILABLE', 700.000, 'TND');
