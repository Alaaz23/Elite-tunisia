-- État V19 : une permission que V20 insère existe déjà (V20 utilise ON CONFLICT DO NOTHING).
INSERT INTO permissions (permission_key, description) VALUES ('services:read', 'pre-existing');
