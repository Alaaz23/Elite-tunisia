-- État V1 : une permission que V2 insérera existe déjà (INSERT sans ON CONFLICT).
INSERT INTO permissions (permission_key, description) VALUES ('users:read', 'pre-existing');
