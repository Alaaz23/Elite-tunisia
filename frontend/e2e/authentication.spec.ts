import { expect, test } from './fixtures';
import { ADMIN_EMAIL, ADMIN_PASSWORD } from './admin-credentials';

test('client registers, reaches profile, is refused admin and logs out', async ({ page }) => {
  const email = `client-${Date.now()}@example.test`;

  await page.goto('/auth/register');
  await page.getByLabel('Prénom').fill('Amira');
  await page.getByLabel('Nom', { exact: true }).fill('Test');
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Téléphone').fill('+216 20 123 456');
  await page.getByLabel('Mot de passe', { exact: true }).fill('Client-Strong-123!');
  await page.getByLabel('Confirmer le mot de passe').fill('Client-Strong-123!');
  const profileRequest = page.waitForRequest((request) => request.url().endsWith('/api/auth/me') && !!request.headers()['authorization']);
  await page.getByRole('button', { name: /Créer mon compte/ }).click();
  const authorization = (await profileRequest).headers()['authorization'];

  await expect(page).toHaveURL(/\/customer\/dashboard$/);
  await expect(page.getByRole('heading', { name: /Bonjour, Amira/ })).toBeVisible();
  await page.getByRole('link', { name: /Mon profil/ }).click();
  await expect(page.getByLabel('Adresse e-mail')).toHaveValue(email);
  await expect(page.getByLabel('Prénom')).toHaveValue('Amira');
  const denied = await page.request.get('/api/admin/vehicles', { headers: { Authorization: authorization } });
  expect(denied.status()).toBe(403);
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/forbidden$/);
  await expect(page.getByRole('heading', { name: /pas pour vous/ })).toBeVisible();

  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto('/customer/profile');
  await expect(page).toHaveURL(/\/auth\/login\?/);
});

test('super admin creates, changes role, disables and blocks an agent login', async ({ page }) => {
  const email = `agent-${Date.now()}@example.test`;
  const password = 'Agent-Strong-123!';

  await page.goto('/auth/admin-login');
  await page.getByLabel('Adresse e-mail').fill(ADMIN_EMAIL);
  await page.getByLabel('Mot de passe').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /Se connecter/ }).click();
  await expect(page).toHaveURL(/\/admin$/);
  await page.getByRole('link', { name: 'Utilisateurs' }).click();
  await expect(page.getByRole('heading', { name: /Utilisateurs internes/ })).toBeVisible();

  await page.getByLabel('Prénom').fill('Agent');
  await page.getByLabel('Nom', { exact: true }).fill('E2E');
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Téléphone').fill('+216 20 222 333');
  await page.getByLabel('Rôle').selectOption('AGENT');
  await page.getByLabel('Intitulé du poste').fill('Agent');
  await page.getByLabel('Mot de passe provisoire').fill(password);
  await page.getByRole('button', { name: 'Créer le compte' }).click();

  const agentRow = page.getByRole('row', { name: new RegExp(email) });
  await expect(agentRow).toBeVisible();
  await agentRow.getByRole('button', { name: 'Modifier' }).click();
  await page.getByLabel('Rôle').selectOption('ACCOUNTANT');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByRole('row', { name: new RegExp(`${email}.*ACCOUNTANT`) })).toBeVisible();

  const updatedRow = page.getByRole('row', { name: new RegExp(email) });
  await updatedRow.getByRole('button', { name: 'Désactiver' }).click();
  await expect(updatedRow.getByText('Désactivé')).toBeVisible();

  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await page.goto('/auth/login');
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: /Se connecter/ }).click();
  await expect(page.getByRole('alert')).toContainText('désactivé');
});