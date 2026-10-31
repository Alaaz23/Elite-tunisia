import { expect, test } from './fixtures';
import { ADMIN_EMAIL, ADMIN_PASSWORD } from './admin-credentials';

test('manager can search the customer directory by registered email', async ({ page }) => {
  const email = `directory-${Date.now()}@example.test`;
  const password = 'Directory-Strong-123!';

  await page.goto('/auth/register');
  await page.getByLabel('Prénom').fill('Directory');
  await page.getByLabel('Nom', { exact: true }).fill('Customer');
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Téléphone').fill('+216 20 123 456');
  await page.getByLabel('Mot de passe', { exact: true }).fill(password);
  await page.getByLabel('Confirmer le mot de passe').fill(password);
  await page.getByRole('button', { name: /Créer mon compte/ }).click();
  await expect(page).toHaveURL(/\/customer\/dashboard$/);

  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page).toHaveURL('/');
  await page.goto('/auth/admin-login');
  await page.getByLabel('Adresse e-mail').fill(ADMIN_EMAIL);
  await page.getByLabel('Mot de passe').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /Se connecter/ }).click();
  await expect(page).toHaveURL(/\/admin$/);

  await page.goto('/admin/customers');
  await expect(page.getByRole('heading', { name: /Clients de l’agence/ })).toBeVisible();
  await page.getByLabel('Rechercher par nom, e-mail ou téléphone').fill(email);
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await expect(page.getByRole('row', { name: new RegExp(email) })).toBeVisible();
});
