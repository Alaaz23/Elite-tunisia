-- État V1 : un employé existe déjà (V2 ajoute first_name/last_name/phone NOT NULL sans valeur par défaut).
INSERT INTO users (id, email, password_hash, role_key)
VALUES ('11111111-1111-1111-1111-111111111111', 'legacy.staff@example.test', 'not-a-real-hash', 'AGENT');
INSERT INTO employees (id, employee_number, job_title)
VALUES ('11111111-1111-1111-1111-111111111111', 'EMP-LEGACY-1', 'Agent');
