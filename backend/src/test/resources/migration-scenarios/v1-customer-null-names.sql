-- État V1 : un client sans prénom, nom ni téléphone (V2 les rend NOT NULL).
INSERT INTO users (id, email, password_hash, role_key)
VALUES ('22222222-2222-2222-2222-222222222222', 'legacy.customer@example.test', 'not-a-real-hash', 'CUSTOMER');
INSERT INTO customers (id) VALUES ('22222222-2222-2222-2222-222222222222');
