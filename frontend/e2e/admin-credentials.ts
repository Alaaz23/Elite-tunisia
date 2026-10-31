// Identifiants du super administrateur utilisés par les scénarios E2E.
// Profil H2 (`test`) : valeurs par défaut du seeder. PostgreSQL : définis par scripts/e2e-postgres.ps1,
// qui génère un mot de passe aléatoire pour la base jetable.
export const ADMIN_EMAIL = process.env['E2E_ADMIN_EMAIL'] ?? 'superadmin@example.test';
export const ADMIN_PASSWORD = process.env['E2E_ADMIN_PASSWORD'] ?? 'Test-Admin-123!';
