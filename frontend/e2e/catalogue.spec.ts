import { expect, test } from './fixtures';
import { ADMIN_EMAIL, ADMIN_PASSWORD } from './admin-credentials';

test('public catalogue applies filters and opens a vehicle detail', async ({ page }) => {
  let filteredUrl = '';
  await page.route('**/api/brands**', (route) => route.fulfill({ json: paged([brand]) }));
  await page.route('**/api/categories**', (route) => route.fulfill({ json: paged([category]) }));
  await page.route('**/api/vehicles**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/vehicle-1')) return route.fulfill({ json: vehicle });
    if (url.searchParams.has('brand')) filteredUrl = url.toString();
    return route.fulfill({ json: paged([vehicle], 0, 1, 1) });
  });

  await page.goto('/vehicles');
  await expect(page.getByRole('heading', { name: /Une flotte choisie/ })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Touring' })).toBeVisible();
  await page.getByLabel('Marque').selectOption('aster');
  await page.getByLabel('Prix / jour min.').fill('65');
  await page.getByRole('button', { name: 'Afficher' }).click();
  await expect.poll(() => filteredUrl).toContain('brand=aster');
  expect(new URL(filteredUrl).searchParams.get('minPrice')).toBe('65');

  await page.getByRole('link', { name: /Découvrir/ }).click();
  await expect(page).toHaveURL(/\/vehicles\/vehicle-1$/);
  await expect(page.getByRole('heading', { name: 'Aster Touring' })).toBeVisible();
  await expect(page.getByText('Un véhicule de test.')).toBeVisible();
});

test('public catalogue distinguishes an empty result from an API failure', async ({ page }) => {
  let unavailable = false;
  await page.route('**/api/brands**', (route) => route.fulfill({ json: paged([]) }));
  await page.route('**/api/categories**', (route) => route.fulfill({ json: paged([]) }));
  await page.route('**/api/vehicles**', (route) => unavailable
    ? route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Service catalogue indisponible.' }) })
    : route.fulfill({ json: paged([]) }));

  await page.goto('/vehicles');
  await expect(page.getByText('Aucun véhicule ne correspond')).toBeVisible();
  unavailable = true;
  await page.reload();
  await expect(page.getByRole('alert')).toContainText('Le catalogue est indisponible.');
  await expect(page.getByRole('button', { name: 'Réessayer' })).toBeVisible();
});

test('super admin opens the fleet editor and submits a vehicle', async ({ page }) => {
  const suffix = Date.now().toString();
  const brandName = `Aster ${suffix}`;
  const categoryName = `Berline ${suffix}`;
  const modelName = `Touring ${suffix}`;

  await page.goto('/auth/admin-login');
  await page.getByLabel('Adresse e-mail').fill(ADMIN_EMAIL);
  await page.getByLabel('Mot de passe').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: /Se connecter/ }).click();
  await expect(page).toHaveURL(/\/admin$/);
  await page.getByRole('link', { name: 'Marques', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Marques du catalogue.' })).toBeVisible();
  await page.getByLabel('Nom', { exact: true }).fill(brandName);
  await page.getByLabel('Description', { exact: true }).fill('Marque de recette E2E.');
  await page.getByRole('button', { name: 'Créer', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: brandName })).toBeVisible();
  await page.getByRole('link', { name: 'Catégories', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Catégories du catalogue.' })).toBeVisible();
  await page.getByLabel('Nom', { exact: true }).fill(categoryName);
  await page.getByLabel('Description', { exact: true }).fill('Catégorie de recette E2E.');
  await page.getByRole('button', { name: 'Créer', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: categoryName })).toBeVisible();
  await page.getByRole('link', { name: 'Véhicules', exact: true }).click();
  await expect(page.getByRole('heading', { name: /Parc véhicules/ })).toBeVisible();
  await page.getByRole('button', { name: /Nouveau véhicule/ }).click();
  await page.getByRole('dialog').getByLabel('Marque').selectOption({ label: brandName });
  await page.getByRole('dialog').getByLabel('Catégorie').selectOption({ label: categoryName });
  await page.getByRole('dialog').getByLabel('Modèle').fill(modelName);
  await page.getByRole('dialog').getByLabel('Prix / jour').fill('180');
  await page.getByRole('dialog').getByLabel('Prix / semaine').fill('1100');
  await page.getByRole('dialog').getByLabel('Description').fill('Véhicule créé pendant un test E2E.');
  const creation = page.waitForResponse((response) => response.url().endsWith('/api/admin/vehicles') && response.request().method() === 'POST');
  await page.getByRole('dialog').getByRole('button', { name: 'Créer le véhicule' }).click();
  const response = await creation;
  expect(response.status()).toBe(200);
  const created = await response.json() as { id: string; model: string; pricePerDay: number };
  await expect(page.getByText('Le véhicule a été créé.', { exact: true })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: modelName })).toBeVisible();
  expect(created).toMatchObject({ model: modelName, pricePerDay: 180 });
  await page.goto(`/vehicles/${created.id}`);
  await expect(page.getByRole('heading', { name: `${brandName} ${modelName}` })).toBeVisible();
  await expect(page.getByText('Véhicule créé pendant un test E2E.', { exact: true })).toBeVisible();
});

const brand = { id: 'brand-1', name: 'Aster', slug: 'aster', description: 'Marque de test.', logoUrl: null, active: true };
const category = { id: 'category-1', name: 'Berline', slug: 'berline', description: 'Berline.', active: true };
const vehicle = {
  id: 'vehicle-1', brand, model: 'Touring', year: 2025, category, description: 'Un véhicule de test.',
  pricePerDay: 80, pricePerWeek: 500, priceWithDriver: null, currency: 'TND', passengers: 5, doors: 4,
  transmission: 'AUTOMATIC', fuel: 'HYBRID', withDriverAvailable: false, status: 'AVAILABLE', images: [], features: ['GPS'],
};

function paged<T>(content: T[], page = 0, totalPages = content.length ? 1 : 0, totalElements = content.length) {
  return { content, page, size: 9, totalElements, totalPages, first: page === 0, last: page + 1 >= totalPages };
}