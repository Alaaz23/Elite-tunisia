import { test as base, expect, type Page, type Route } from '@playwright/test';

// Parcours navigateur du Lot 7. Tous les appels /api sont simulés ici :
// aucun backend ni base de données n'est touché par ces tests.
// Les identifiants sont des fixtures factices réservées aux tests.

const CUSTOMER_EMAIL = 'client.lot7@example.invalid';
const CUSTOMER_PASSWORD = 'lot7-fixture-password';

type PaymentOutcome = 'CAPTURED' | 'FAILED' | 'PENDING' | 'HOLD_EXPIRED';

interface BackendState {
  refreshCookieValid: boolean;
  validAccessToken: string | null;
  tokenSeq: number;
  refreshCalls: number;
  rejectedCalls: number;
  paymentCalls: number;
  paymentOutcome: PaymentOutcome;
  quoteStatus: 'SENT' | 'ACCEPTED';
  availability: boolean;
  unhandled: string[];
}

function newState(): BackendState {
  return {
    refreshCookieValid: false,
    validAccessToken: null,
    tokenSeq: 0,
    refreshCalls: 0,
    rejectedCalls: 0,
    paymentCalls: 0,
    paymentOutcome: 'CAPTURED',
    quoteStatus: 'ACCEPTED',
    availability: true,
    unhandled: [],
  };
}

const NOW = Date.now();
const START = new Date(NOW + 3 * 86_400_000);
START.setHours(10, 0, 0, 0);
const END = new Date(START.getTime() + 2 * 86_400_000);
const START_ISO = START.toISOString();
const END_ISO = END.toISOString();
const NOW_ISO = new Date(NOW).toISOString();
const HOLD_ISO = new Date(NOW + 30 * 60_000).toISOString();

function localInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

const brandPorsche = { id: 'brand-porsche', name: 'Porsche', slug: 'porsche', description: null, logoUrl: null, active: true };
const brandMercedes = { id: 'brand-mercedes', name: 'Mercedes-Benz', slug: 'mercedes-benz', description: null, logoUrl: null, active: true };
const brandFixture = { id: 'brand-fx', name: 'Elite Fixture', slug: 'elite-fixture', description: null, logoUrl: null, active: true };
const catSport = { id: 'cat-sport', name: 'Sport', slug: 'sport', description: null, active: true };
const catBerline = { id: 'cat-berline', name: 'Berline', slug: 'berline', description: null, active: true };

function vehicle(id: string, brand: typeof brandPorsche, model: string, category: typeof catSport) {
  return {
    id,
    brand,
    model,
    year: 2024,
    category,
    description: 'Fiche de test',
    pricePerDay: 600,
    pricePerWeek: 3600,
    priceWithDriver: null,
    currency: 'TND',
    passengers: 2,
    doors: 2,
    transmission: 'AUTOMATIC',
    fuel: 'PETROL',
    withDriverAvailable: false,
    status: 'AVAILABLE',
    images: [],
    features: [],
  };
}

const VEHICLES = [
  vehicle('veh-1', brandPorsche, '911 Carrera', catSport),
  vehicle('veh-2', brandMercedes, 'Classe S', catBerline),
  ...Array.from({ length: 23 }, (_, i) => vehicle(`veh-fx-${i + 3}`, brandFixture, `Modèle test ${i + 1}`, catSport)),
];

const VEHICLE_BY_ID = new Map(VEHICLES.map((v) => [v.id, v]));

const USER = {
  id: 'cust-1',
  firstName: 'Client',
  lastName: 'Lot7',
  email: CUSTOMER_EMAIL,
  phone: '+216 00 000 000',
  roles: ['CUSTOMER'],
  status: 'ACTIVE',
};

function quote(status: 'SENT' | 'ACCEPTED') {
  return {
    id: 'quote-1',
    reference: 'ELT-Q-0001',
    bookingId: 'bk-1',
    bookingReference: 'ELT-B-0001',
    bookingStatus: 'PENDING',
    customerId: 'cust-1',
    customerName: 'Client Lot7',
    vehicleId: 'veh-1',
    vehicleName: 'Porsche 911 Carrera',
    startDateTime: START_ISO,
    endDateTime: END_ISO,
    pickupLocation: 'Tunis Carthage',
    dropoffLocation: 'Tunis Carthage',
    version: 1,
    status,
    currency: 'TND',
    subtotal: 1200,
    discountAmount: 0,
    taxAmount: 0,
    totalAmount: 1200,
    validUntil: null,
    customerNote: null,
    internalNote: null,
    createdAt: NOW_ISO,
    updatedAt: NOW_ISO,
    sentAt: NOW_ISO,
    acceptedAt: status === 'ACCEPTED' ? NOW_ISO : null,
    rejectedAt: null,
    businessTimeZone: 'Africa/Tunis',
    lines: [
      { id: 'line-1', type: 'RENTAL', description: 'Location 2 jours', quantity: 2, unitPrice: 600, amount: 1200, metadata: null, position: 1 },
    ],
    history: [],
  };
}

const BOOKING = {
  id: 'bk-1',
  reference: 'ELT-B-0001',
  customerId: 'cust-1',
  customerName: 'Client Lot7',
  vehicleId: 'veh-1',
  vehicleName: 'Porsche 911 Carrera',
  startDateTime: START_ISO,
  endDateTime: END_ISO,
  pickupLocation: 'Tunis Carthage',
  dropoffLocation: 'Tunis Carthage',
  status: 'PENDING',
  customerNote: null,
  internalNote: null,
  createdAt: NOW_ISO,
  updatedAt: NOW_ISO,
  confirmedAt: null,
  cancelledAt: null,
  holdExpiresAt: HOLD_ISO,
  history: [],
};

const INVOICE_SUMMARY = { id: 'inv-1', reference: 'ELT-INV-0001', totalAmount: 1200, currency: 'TND', issuedAt: NOW_ISO };
const INVOICE_DETAIL = { ...INVOICE_SUMMARY, lines: [] };

function pageOf<T>(content: T[], page: number, size: number) {
  const totalPages = Math.max(1, Math.ceil(content.length / size));
  return {
    content: content.slice(page * size, page * size + size),
    page,
    size,
    totalElements: content.length,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  };
}

function tokenResponse(state: BackendState) {
  state.tokenSeq += 1;
  state.validAccessToken = `access-lot7-${state.tokenSeq}`;
  return { accessToken: state.validAccessToken, expiresIn: 900, userId: 'cust-1', email: CUSTOMER_EMAIL, roles: ['CUSTOMER'] };
}

async function installBackend(page: Page, state: BackendState): Promise<void> {
  await page.route('**/api/**', async (route: Route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace(/^\/api/, '');
    const method = req.method();

    const reply = (status: number, body?: unknown) =>
      route.fulfill({ status, contentType: 'application/json', body: body === undefined ? '' : JSON.stringify(body) });
    const isAuthed = () => {
      const ok = state.validAccessToken !== null && req.headers()['authorization'] === `Bearer ${state.validAccessToken}`;
      if (!ok) state.rejectedCalls += 1;
      return ok;
    };
    const denied = () => reply(401, { code: 'UNAUTHORIZED', message: 'Session expirée.' });

    if (path === '/auth/csrf') return reply(200, { token: 'csrf-lot7' });

    if (path === '/auth/login' && method === 'POST') {
      const body = req.postDataJSON() as { email?: string; password?: string };
      if (body.email !== CUSTOMER_EMAIL || body.password !== CUSTOMER_PASSWORD) {
        return reply(401, { code: 'INVALID_CREDENTIALS', message: 'Identifiants invalides.' });
      }
      state.refreshCookieValid = true;
      return reply(200, tokenResponse(state));
    }

    if (path === '/auth/refresh' && method === 'POST') {
      state.refreshCalls += 1;
      if (!state.refreshCookieValid) return reply(401, { code: 'REFRESH_TOKEN_INVALID', message: 'Session expirée.' });
      return reply(200, tokenResponse(state));
    }

    if (path === '/auth/logout' && method === 'POST') {
      state.refreshCookieValid = false;
      state.validAccessToken = null;
      return reply(204);
    }

    if (path === '/auth/me') return isAuthed() ? reply(200, USER) : denied();

    // Catalogue public
    if (path === '/vehicles' || path === '/vehicles/available') {
      const search = (url.searchParams.get('search') ?? '').trim().toLowerCase();
      const size = Number(url.searchParams.get('size') ?? 12);
      const page = Number(url.searchParams.get('page') ?? 0);
      const filtered = VEHICLES.filter((v) => `${v.brand.name} ${v.model}`.toLowerCase().includes(search));
      return reply(200, pageOf(filtered, page, size));
    }

    const availabilityMatch = /^\/vehicles\/([^/]+)\/availability$/.exec(path);
    if (availabilityMatch) {
      return reply(200, {
        available: state.availability,
        reason: state.availability ? null : 'Véhicule déjà réservé sur cette période.',
      });
    }

    const vehicleMatch = /^\/vehicles\/([^/]+)$/.exec(path);
    if (vehicleMatch) {
      const found = VEHICLE_BY_ID.get(vehicleMatch[1]);
      return found ? reply(200, found) : reply(404, { code: 'VEHICLE_NOT_FOUND', message: 'Véhicule introuvable.' });
    }

    if (path === '/categories') return reply(200, pageOf([catSport, catBerline], 0, 50));
    if (path === '/brands') return reply(200, pageOf([brandPorsche, brandMercedes, brandFixture], 0, 50));

    // Réservation
    if (path === '/bookings/policy') {
      return reply(200, { businessTimeZone: 'Africa/Tunis', pendingBlocks: true, priceRule: 'DAILY', options: [] });
    }
    if (path === '/bookings/quote-preview' && method === 'POST') {
      return reply(200, {
        vehicleId: 'veh-1',
        vehicleName: 'Porsche 911 Carrera',
        startDateTime: START_ISO,
        endDateTime: END_ISO,
        numberOfDays: 2,
        basePrice: 1200,
        additionalCharges: 0,
        discount: 0,
        tax: 0,
        totalAmount: 1200,
        currency: 'TND',
        businessTimeZone: 'Africa/Tunis',
      });
    }
    if (path === '/bookings' && method === 'POST') return isAuthed() ? reply(201, BOOKING) : denied();
    if (path === '/bookings' && method === 'GET') return isAuthed() ? reply(200, pageOf([BOOKING], 0, 20)) : denied();
    if (path === '/bookings/bk-1') return isAuthed() ? reply(200, BOOKING) : denied();

    // Devis et paiement
    if (path === '/customer/quotes') return isAuthed() ? reply(200, pageOf([quote(state.quoteStatus)], 0, 20)) : denied();
    if (path === '/customer/quotes/quote-1') return isAuthed() ? reply(200, quote(state.quoteStatus)) : denied();
    if (path === '/customer/quotes/quote-1/accept' && method === 'POST') {
      if (!isAuthed()) return denied();
      state.quoteStatus = 'ACCEPTED';
      return reply(200, quote('ACCEPTED'));
    }
    if (path === '/customer/quotes/quote-1/payments' && method === 'POST') {
      if (!isAuthed()) return denied();
      state.paymentCalls += 1;
      switch (state.paymentOutcome) {
        case 'HOLD_EXPIRED':
          return reply(409, { code: 'BOOKING_HOLD_EXPIRED', message: 'Le délai de réservation est dépassé.' });
        case 'FAILED':
          return reply(201, { id: 'pay-1', status: 'FAILED', transactionRef: null, amount: 1200 });
        case 'PENDING':
          return reply(201, { id: 'pay-1', status: 'PENDING', transactionRef: null, amount: 1200 });
        default:
          return reply(201, { id: 'pay-1', status: 'CAPTURED', transactionRef: 'TXN-LOT7-0001', amount: 1200 });
      }
    }

    // Profil et factures
    if (path === '/customer/profile') {
      return isAuthed()
        ? reply(200, { id: 'cust-1', firstName: USER.firstName, lastName: USER.lastName, email: USER.email, phone: USER.phone })
        : denied();
    }
    if (path === '/invoices') return isAuthed() ? reply(200, [INVOICE_SUMMARY]) : denied();
    if (path === '/invoices/inv-1') return isAuthed() ? reply(200, INVOICE_DETAIL) : denied();
    if (path === '/invoices/inv-1/pdf') {
      if (!isAuthed()) return denied();
      return route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        headers: { 'content-disposition': 'attachment; filename="ELT-INV-0001.pdf"' },
        body: Buffer.from('%PDF-1.4\n% fixture Lot 7\n%%EOF\n'),
      });
    }
    if (path === '/invoices/inv-404') return isAuthed() ? reply(404, { code: 'INVOICE_NOT_FOUND', message: 'Facture introuvable.' }) : denied();
    if (path === '/invoices/inv-forbidden') return isAuthed() ? reply(403, { code: 'ACCESS_DENIED', message: 'Accès refusé.' }) : denied();

    state.unhandled.push(`${method} ${path}`);
    return reply(404, { code: 'NOT_MOCKED', message: 'Route non simulée.' });
  });
}

const test = base.extend<{ backend: BackendState }>({
  backend: async ({ page }, use) => {
    const state = newState();
    await installBackend(page, state);
    await use(state);
    expect(state.unhandled, 'Appels /api non simulés pendant le test').toEqual([]);
  },
});

async function signIn(page: Page, returnUrl: string): Promise<void> {
  await page.goto(`/auth/login?returnUrl=${encodeURIComponent(returnUrl)}`);
  await fillLogin(page);
  const path = returnUrl.split('?')[0];
  await expect(page).toHaveURL((url) => url.pathname === path);
}

async function fillLogin(page: Page): Promise<void> {
  await page.locator('#login-email').fill(CUSTOMER_EMAIL);
  await page.locator('#login-password').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

async function fillAvailability(page: Page): Promise<void> {
  await page.getByLabel('Début de location').fill(localInput(START));
  await page.getByLabel('Fin de location').fill(localInput(END));
  await page.getByLabel('Lieu de prise en charge').fill('Aéroport de Tunis-Carthage');
  await page.getByLabel('Lieu de retour').fill('Aéroport de Tunis-Carthage');
}

test.describe('Accueil et navigation', () => {
  test('affiche l’accueil, la navigation et transmet la recherche au catalogue', async ({ page, backend: _backend }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Une mobilité');
    await expect(page.getByRole('link', { name: 'La flotte' }).first()).toBeVisible();

    await page.getByLabel('Modèle ou marque').fill('Porsche');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page).toHaveURL((url) => url.pathname === '/vehicles' && url.searchParams.get('search') === 'Porsche');
  });
});

test.describe('Catalogue et détail véhicule', () => {
  test('consulte le catalogue', async ({ page, backend: _backend }) => {
    await page.goto('/vehicles');
    await expect(page.getByRole('heading', { name: /Une flotte choisie/ })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Voir Porsche 911 Carrera' })).toBeVisible();
  });

  test('filtre les véhicules par recherche', async ({ page, backend: _backend }) => {
    await page.goto('/vehicles');
    await page.getByLabel('Recherche').fill('Porsche');
    await page.getByRole('button', { name: 'Afficher' }).click();
    await expect(page.getByRole('link', { name: 'Voir Porsche 911 Carrera' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Voir Mercedes-Benz Classe S' })).toHaveCount(0);
    await expect(page).toHaveURL((url) => url.searchParams.get('search') === 'Porsche');
  });

  test('navigue entre les pages du catalogue', async ({ page, backend: _backend }) => {
    await page.goto('/vehicles');
    const pager = page.getByRole('navigation', { name: 'Pagination du catalogue' });
    await expect(pager).toContainText(/Page 1 \//);
    await pager.getByRole('button', { name: /Suivant/ }).click();
    await expect(pager).toContainText(/Page 2 \//);
  });

  test('affiche le détail d’un véhicule disponible', async ({ page, backend: _backend }) => {
    await page.goto('/vehicles/veh-1');
    await expect(page.getByText('Ouvert à la réservation')).toBeVisible();
  });
});

test.describe('Disponibilité et réservation', () => {
  test('visiteur : disponibilité confirmée puis invitation à se connecter', async ({ page, backend: _backend }) => {
    await page.goto('/vehicles/veh-1');
    await fillAvailability(page);
    await page.getByRole('button', { name: 'Vérifier disponibilité' }).click();
    await expect(page.getByText('Disponible pour cette période.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Connexion client' })).toBeVisible();
  });

  test('visiteur : véhicule indisponible sur la période', async ({ page, backend }) => {
    backend.availability = false;
    await page.goto('/vehicles/veh-1');
    await fillAvailability(page);
    await page.getByRole('button', { name: 'Vérifier disponibilité' }).click();
    await expect(page.getByText('Indisponible pour cette période.')).toBeVisible();
  });

  test('client connecté : crée une demande de réservation', async ({ page, backend }) => {
    await signIn(page, '/vehicles/veh-1');
    await fillAvailability(page);
    await page.getByRole('button', { name: 'Vérifier disponibilité' }).click();
    await expect(page.getByText('Disponible pour cette période.')).toBeVisible();
    await page.getByRole('button', { name: 'Envoyer ma demande' }).click();
    await expect(page).toHaveURL((url) => url.pathname === '/customer/bookings/bk-1');
    expect(backend.paymentCalls).toBe(0);
  });
});

test.describe('Devis et paiement simulé', () => {
  test('accepte un devis envoyé puis propose le paiement', async ({ page, backend }) => {
    backend.quoteStatus = 'SENT';
    await signIn(page, '/customer/quotes/quote-1');
    await page.getByRole('button', { name: 'Accepter le devis' }).click();
    await expect(page.getByText('Votre réponse est enregistrée.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Payer' })).toBeVisible();
  });

  test('paiement CAPTURED : confirmation affichée une seule fois', async ({ page, backend }) => {
    await signIn(page, '/customer/quotes/quote-1');
    await expect(page.getByRole('button', { name: 'Payer' })).toBeVisible();
    await page.getByRole('button', { name: 'Payer' }).dblclick();
    await expect(page.getByText('Paiement réussi.')).toBeVisible();
    await expect(page.getByText(/TXN-LOT7-0001/)).toBeVisible();
    await page.waitForTimeout(500);
    expect(backend.paymentCalls).toBe(1);
  });

  test('paiement FAILED : aucun succès affiché', async ({ page, backend }) => {
    backend.paymentOutcome = 'FAILED';
    await signIn(page, '/customer/quotes/quote-1');
    await page.getByRole('button', { name: 'Payer' }).click();
    await expect(page.getByText('Échec du paiement.')).toBeVisible();
    await expect(page.getByText('Paiement réussi.')).toHaveCount(0);
  });

  test('paiement PENDING : pas de confirmation prématurée', async ({ page, backend }) => {
    backend.paymentOutcome = 'PENDING';
    await signIn(page, '/customer/quotes/quote-1');
    await page.getByRole('button', { name: 'Payer' }).click();
    await expect(page.getByText(/Paiement en cours de vérification/)).toBeVisible();
    await expect(page.getByText('Paiement réussi.')).toHaveCount(0);
  });

  test('hold expiré : message explicite, pas de succès, reprise possible', async ({ page, backend }) => {
    backend.paymentOutcome = 'HOLD_EXPIRED';
    await signIn(page, '/customer/quotes/quote-1');
    await page.getByRole('button', { name: 'Payer' }).click();
    await expect(page.getByText(/La réservation a expiré/)).toBeVisible();
    await expect(page.getByText('Paiement réussi.')).toHaveCount(0);
    await page.getByRole('button', { name: 'Vérifier la disponibilité' }).click();
    await expect(page.getByText('Le véhicule est disponible pour ces dates.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Reprendre la réservation' })).toHaveAttribute('href', '/vehicles/veh-1');
  });

  test('hold expiré avec véhicule indisponible', async ({ page, backend }) => {
    backend.paymentOutcome = 'HOLD_EXPIRED';
    backend.availability = false;
    await signIn(page, '/customer/quotes/quote-1');
    await page.getByRole('button', { name: 'Payer' }).click();
    await expect(page.getByText(/La réservation a expiré/)).toBeVisible();
    await page.getByRole('button', { name: 'Vérifier la disponibilité' }).click();
    await expect(page.getByText(/n.est plus disponible pour ces dates/)).toBeVisible();
    await expect(page.getByRole('link', { name: 'Reprendre la réservation' })).toHaveCount(0);
  });
});

test.describe('Espace client', () => {
  test('tableau de bord, devis, réservations et profil', async ({ page, backend: _backend }) => {
    await signIn(page, '/customer/dashboard');
    await expect(page.getByRole('heading', { name: /Bonjour, Client/ })).toBeVisible();

    await page.getByRole('link', { name: 'Mes devis' }).click();
    await expect(page.getByText('ELT-Q-0001')).toBeVisible();

    await page.getByRole('link', { name: 'Réservations', exact: true }).click();
    await expect(page.getByText('ELT-B-0001')).toBeVisible();

    await page.getByRole('link', { name: 'Profil', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Mon profil.' })).toBeVisible();
  });

  test('liste des factures, détail et téléchargement du PDF', async ({ page, backend: _backend }) => {
    await signIn(page, '/customer/invoices');
    await expect(page.getByRole('heading', { name: 'Mes factures' })).toBeVisible();
    await page.getByRole('link', { name: 'ELT-INV-0001' }).click();
    await expect(page.getByRole('heading', { name: /Facture ELT-INV-0001/ })).toBeVisible();

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Télécharger le PDF' }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('ELT-INV-0001.pdf');
  });

  test('facture inexistante : message sans fuite d’information', async ({ page, backend: _backend }) => {
    await signIn(page, '/customer/invoices/inv-404');
    await expect(page.getByText(/introuvable ou ne vous est pas accessible/)).toBeVisible();
  });

  test('facture d’un autre utilisateur : accès refusé affiché', async ({ page, backend: _backend }) => {
    await signIn(page, '/customer/invoices/inv-forbidden');
    await expect(page.getByText(/pas accès à cette facture/)).toBeVisible();
  });
});

test.describe('Sessions et accès protégés', () => {
  test('access token expiré : renouvellement unique puis paiement réussi', async ({ page, backend }) => {
    await signIn(page, '/customer/quotes/quote-1');
    await expect(page.getByRole('button', { name: 'Payer' })).toBeVisible();
    const refreshBefore = backend.refreshCalls;

    backend.validAccessToken = null;
    await page.getByRole('button', { name: 'Payer' }).click();

    await expect(page.getByText('Paiement réussi.')).toBeVisible();
    expect(backend.refreshCalls - refreshBefore).toBe(1);
    expect(backend.rejectedCalls).toBeGreaterThanOrEqual(1);
  });

  test('refresh token invalide : session nettoyée, redirection vers la connexion puis retour', async ({ page, backend }) => {
    await signIn(page, '/customer/quotes/quote-1');
    await expect(page.getByRole('button', { name: 'Payer' })).toBeVisible();

    backend.validAccessToken = null;
    backend.refreshCookieValid = false;
    await page.getByRole('button', { name: 'Payer' }).click();

    await expect(page).toHaveURL(
      (url) => url.pathname === '/auth/login' && url.searchParams.get('returnUrl') === '/customer/quotes/quote-1',
    );
    await expect(page.getByText('Paiement réussi.')).toHaveCount(0);

    await fillLogin(page);
    await expect(page).toHaveURL((url) => url.pathname === '/customer/quotes/quote-1');
  });

  test('visiteur sur une route protégée : redirection vers la connexion', async ({ page, backend: _backend }) => {
    await page.goto('/customer/dashboard');
    await expect(page).toHaveURL(
      (url) => url.pathname === '/auth/login' && url.searchParams.get('returnUrl') === '/customer/dashboard',
    );
  });

  test('client connecté sur une route d’administration : accès refusé', async ({ page, backend: _backend }) => {
    await signIn(page, '/customer/dashboard');
    await page.goto('/admin');
    await expect(page).toHaveURL((url) => url.pathname === '/forbidden');
    await expect(page.getByRole('heading', { name: /Cette page n.est pas pour vous/ })).toBeVisible();
  });
});
