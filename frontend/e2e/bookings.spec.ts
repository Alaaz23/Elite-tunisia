import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { Temporal } from '@js-temporal/polyfill';

test.describe.serial('Booking engine with real API', () => {
  const suffix = Date.now().toString();
  const password = 'Booking-Strong-123!';
  const customerEmail = `booking-owner-${suffix}@example.test`;
  const otherEmail = `booking-other-${suffix}@example.test`;
  const agentEmail = `booking-agent-${suffix}@example.test`;
  const model = `Booking Touring ${suffix}`;
  const start = Temporal.Now.zonedDateTimeISO('Africa/Tunis').add({ days: 45 }).with({ hour: 10, minute: 0, second: 0, millisecond: 0, microsecond: 0, nanosecond: 0 });
  const end = start.add({ days: 2 });
  let vehicleId = ''; let bookingId = ''; let reference = '';

  test.beforeAll(async ({ request }) => {
    const admin = await apiLogin(request, 'superadmin@example.test', 'Test-Admin-123!');
    const brand = await mutate(request, admin, 'POST', '/api/admin/brands', { name: `Booking Brand ${suffix}`, description: 'E2E booking brand', active: true });
    const category = await mutate(request, admin, 'POST', '/api/admin/categories', { name: `Booking Category ${suffix}`, description: 'E2E booking category', active: true });
    const vehicle = await mutate(request, admin, 'POST', '/api/admin/vehicles', {
      brandId: brand.id, categoryId: category.id, model, year: 2025, description: 'Real booking E2E vehicle',
      pricePerDay: 125.123, pricePerWeek: 750, currency: 'TND', passengers: 5, doors: 4,
      transmission: 'AUTOMATIC', fuel: 'PETROL', withDriverAvailable: false, status: 'AVAILABLE', features: [], images: [],
    });
    vehicleId = vehicle.id as string;
    await mutate(request, admin, 'POST', '/api/admin/users', { firstName: 'Booking', lastName: 'Agent', email: agentEmail,
      phone: '+216 20 123 456', password, role: 'AGENT', jobTitle: 'Agent' });
    for (const email of [customerEmail, otherEmail]) {
      const csrf = await csrfToken(request);
      const registration = await request.post('/api/auth/register', { headers: { 'X-XSRF-TOKEN': csrf }, data: {
        firstName: 'Booking', lastName: 'Client', email, phone: '+216 20 123 456', password, confirmPassword: password,
      } });
      expect(registration.status()).toBe(201);
    }
  });

  test('client logs in, checks a vehicle, previews and creates a PENDING request', async ({ page }) => {
    await login(page, customerEmail, password);
    await page.goto('/vehicles');
    await page.getByLabel('Recherche', { exact: true }).fill(model);
    await page.getByRole('button', { name: 'Afficher', exact: true }).click();
    await expect(page.getByRole('heading', { name: model, exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Découvrir', exact: true }).click();
    await page.getByLabel('Début de location').fill(start.toPlainDateTime().toString({ smallestUnit: 'minute' }));
    await page.getByLabel('Fin de location').fill(end.toPlainDateTime().toString({ smallestUnit: 'minute' }));
    await page.getByLabel('Lieu de prise en charge').fill('Aéroport Tunis-Carthage');
    await page.getByLabel('Lieu de retour').fill('Tunis centre');
    await page.getByRole('button', { name: 'Vérifier disponibilité' }).click();
    await expect(page.getByRole('region', { name: 'Récapitulatif' })).toBeVisible();
    await expect(page.getByText('Disponible pour cette période.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Envoyer ma demande' }).click();
    await expect(page).toHaveURL(/\/customer\/bookings\/[a-f0-9-]+$/);
    bookingId = page.url().split('/').at(-1)!;
    reference = await page.getByRole('heading', { level: 1 }).innerText();
    expect(reference).toMatch(/^ET-\d{4}-\d{6,}$/);
    await expect(page.getByText(`Réservation ${reference} créée.`, { exact: true })).toBeVisible();
    await expect(page.locator('header .badge')).toHaveText('En attente');
    await page.getByRole('link', { name: 'Retour aux réservations' }).click();
    await expect(page.getByRole('link', { name: reference, exact: true })).toBeVisible();
  });

  test('authorized agent finds and confirms the request; overlap is refused', async ({ page, request }) => {
    await login(page, agentEmail, password, true);
    await page.getByRole('link', { name: 'Réservations', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Réservations', exact: true })).toBeVisible();
    await page.getByLabel('Référence', { exact: true }).fill(reference);
    await page.getByRole('button', { name: 'Rechercher', exact: true }).click();
    await page.getByRole('link', { name: reference, exact: true }).click();
    await page.getByRole('button', { name: 'Confirmer', exact: true }).click();
    await expect(page.locator('header .badge')).toHaveText('Confirmée');
    const token = await apiLogin(request, otherEmail, password);
    const csrf = await csrfToken(request);
    const conflict = await request.post('/api/bookings', { headers: { Authorization: `Bearer ${token}`, 'X-XSRF-TOKEN': csrf }, data: {
      vehicleId, startDateTime: start.toInstant().toString(), endDateTime: end.toInstant().toString(), pickupLocation: 'Tunis', dropoffLocation: 'Tunis',
    } });
    expect(conflict.status()).toBe(409); expect((await conflict.json()).code).toBe('VEHICLE_NOT_AVAILABLE');
    const availability = await request.get(`/api/vehicles/${vehicleId}/availability`, { params: { startDateTime: start.toInstant().toString(), endDateTime: end.toInstant().toString() } });
    expect((await availability.json()).available).toBe(false);
    await page.goto('/vehicles');
    await page.getByLabel('Recherche', { exact: true }).fill(model);
    await page.getByLabel('Début de période').fill(start.toPlainDateTime().toString({ smallestUnit: 'minute' }));
    await page.getByLabel('Fin de période').fill(end.toPlainDateTime().toString({ smallestUnit: 'minute' }));
    await page.getByRole('button', { name: 'Afficher', exact: true }).click();
    await expect(page.getByText('Aucun véhicule ne correspond', { exact: true })).toBeVisible();
  });

  test('another customer cannot read or cancel the owned booking', async ({ page, request }) => {
    const token = await apiLogin(request, otherEmail, password);
    const denied = await request.get(`/api/bookings/${bookingId}`, { headers: { Authorization: `Bearer ${token}` } });
    expect(denied.status()).toBe(404);
    const csrf = await csrfToken(request);
    const cancel = await request.post(`/api/bookings/${bookingId}/cancel`, { headers: { Authorization: `Bearer ${token}`, 'X-XSRF-TOKEN': csrf }, data: {} });
    expect(cancel.status()).toBe(404);
    await login(page, otherEmail, password);
    await page.goto(`/customer/bookings/${bookingId}`);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('heading', { name: reference, exact: true })).toHaveCount(0);
  });

  test('owner cancels and the confirmed period becomes available again', async ({ page, request }) => {
    await login(page, customerEmail, password);
    await page.goto(`/customer/bookings/${bookingId}`);
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Annuler la réservation' }).click();
    await expect(page.locator('header .badge')).toHaveText('Annulée');
    const response = await request.get(`/api/vehicles/${vehicleId}/availability`, { params: { startDateTime: start.toInstant().toString(), endDateTime: end.toInstant().toString() } });
    expect((await response.json()).available).toBe(true);
  });
});

async function login(page: Page, email: string, password: string, admin = false) {
  await page.goto(admin ? '/auth/admin-login' : '/auth/login');
  await page.getByLabel('Adresse e-mail').fill(email); await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: /Se connecter/ }).click();
  await expect(page).toHaveURL(admin ? /\/admin$/ : /\/customer\/dashboard$/);
}
async function csrfToken(request: APIRequestContext): Promise<string> { return (await (await request.get('/api/auth/csrf')).json()).token as string; }
async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const csrf = await csrfToken(request);
  const response = await request.post('/api/auth/login', { headers: { 'X-XSRF-TOKEN': csrf }, data: { email, password } });
  expect(response.status()).toBe(200); return (await response.json()).accessToken as string;
}
async function mutate(request: APIRequestContext, token: string, method: string, url: string, data: unknown): Promise<Record<string, unknown>> {
  const csrf = await csrfToken(request);
  const response = await request.fetch(url, { method, headers: { Authorization: `Bearer ${token}`, 'X-XSRF-TOKEN': csrf }, data });
  expect(response.ok(), `Mutation ${method} ${url}: ${response.status()}`).toBe(true);
  return await response.json() as Record<string, unknown>;
}