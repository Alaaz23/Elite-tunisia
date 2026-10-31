import { APIRequestContext } from '@playwright/test';
import { expect, test } from './fixtures';
import { ADMIN_EMAIL, ADMIN_PASSWORD } from './admin-credentials';

test.describe.serial('Service catalogue', () => {
  const suffix = Date.now().toString();
  const password = 'Service-Strong-123!';
  const agentEmail = `service-agent-${suffix}@example.test`;
  const serviceName = `Siège bébé ${suffix}`;

  test.beforeAll(async ({ request }) => {
    const admin = await apiLogin(request, ADMIN_EMAIL, ADMIN_PASSWORD);
    const csrf = await csrfToken(request);
    const response = await request.post('/api/admin/users', { headers: { Authorization: `Bearer ${admin}`, 'X-XSRF-TOKEN': csrf }, data: {
      firstName: 'Service', lastName: 'Agent', email: agentEmail, phone: '+216 20 123 456', password, role: 'AGENT', jobTitle: 'Agent' } });
    expect(response.ok(), `création de l'agent : ${response.status()}`).toBe(true);
  });

  test('manager creates, edits, deactivates and reactivates a service', async ({ page }) => {
    await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await page.getByRole('link', { name: 'Services', exact: true }).click();
    await expect(page.getByRole('heading', { name: /Services additionnels/ })).toBeVisible();

    await page.getByLabel('Nom du service').fill(serviceName);
    await page.getByLabel('Description').fill('Siège homologué 0-4 ans');
    await page.getByLabel('Prix unitaire').fill('12.5');
    await page.getByRole('button', { name: 'Créer le service' }).click();
    const row = page.locator('tr', { hasText: serviceName });
    await expect(row).toContainText('12.500 TND');
    await expect(row).toContainText('Actif');

    await page.getByLabel('Nom du service').fill(serviceName);
    await page.getByLabel('Prix unitaire').fill('1.2345');
    await page.getByRole('button', { name: 'Créer le service' }).click();
    await expect(page.getByText('Indiquez un prix supérieur à 0, avec au plus trois décimales.')).toBeVisible();
    await page.getByLabel('Prix unitaire').fill('15');
    await page.getByRole('button', { name: 'Créer le service' }).click();
    await expect(page.getByRole('alert')).toContainText('Un service actif porte déjà ce nom');

    await row.getByRole('button', { name: 'Modifier' }).click();
    await expect(page.getByLabel('Devise')).toHaveAttribute('readonly', '');
    await page.getByLabel('Prix unitaire').fill('14');
    await page.getByRole('button', { name: 'Enregistrer le service' }).click();
    await expect(page.locator('tr', { hasText: serviceName })).toContainText('14.000 TND');

    await page.locator('tr', { hasText: serviceName }).getByRole('button', { name: 'Désactiver' }).click();
    await expect(page.locator('tr', { hasText: serviceName })).toContainText('Inactif');
    await page.locator('tr', { hasText: serviceName }).getByRole('button', { name: 'Réactiver' }).click();
    await expect(page.locator('tr', { hasText: serviceName })).toContainText('Actif');
  });

  test('an agent can consult the catalogue but the server refuses any write', async ({ page, request }) => {
    await login(page, agentEmail, password);
    await page.goto('/admin/services');
    await expect(page.locator('tr', { hasText: serviceName })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Créer le service' })).toHaveCount(0);
    await expect(page.getByText('Votre rôle autorise la consultation du catalogue.')).toBeVisible();

    const token = await apiLogin(request, agentEmail, password);
    const csrf = await csrfToken(request);
    const headers = { Authorization: `Bearer ${token}`, 'X-XSRF-TOKEN': csrf };
    const forged = await request.post('/api/admin/services', { headers, data: { name: 'Service interdit', description: '', price: 5, currency: 'TND' } });
    expect(forged.status()).toBe(403);
    const list = await request.get('/api/admin/services', { headers, params: { search: serviceName } });
    expect(list.status()).toBe(200);
    expect((await list.json()).totalElements).toBe(1);
  });
});

async function login(page: import('@playwright/test').Page, email: string, password: string) {
  await page.goto('/auth/admin-login');
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: /Se connecter/ }).click();
  await expect(page).toHaveURL(/\/admin$/);
}
async function csrfToken(request: APIRequestContext): Promise<string> { return (await (await request.get('/api/auth/csrf')).json()).token as string; }
async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const csrf = await csrfToken(request);
  const response = await request.post('/api/auth/login', { headers: { 'X-XSRF-TOKEN': csrf }, data: { email, password } });
  expect(response.status()).toBe(200);
  return (await response.json()).accessToken as string;
}
