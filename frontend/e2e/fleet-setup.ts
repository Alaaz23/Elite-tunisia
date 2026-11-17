import { APIRequestContext } from '@playwright/test';

// Un véhicule n'est réservable qu'avec une garantie partenaire ACTIVE couvrant la période (durée max 90 jours).
export async function grantFleetGuarantee(
  request: APIRequestContext,
  adminToken: string,
  vehicleId: string,
  suffix: string,
  startsAt: string,
  endsAt: string,
): Promise<void> {
  const partner = await adminPost(request, adminToken, '/api/admin/partners', {
    partnerType: 'COMPANY', name: `E2E Partner ${suffix}`, email: `partner-${suffix}@example.test`,
    phone: '+216 20 123 456', address: 'Tunis', city: 'Tunis', taxIdentifier: `E2E-TAX-${suffix}`.slice(0, 40), userId: null,
  });
  await adminPost(request, adminToken, `/api/admin/vehicles/${vehicleId}/partner-assignments`, {
    partnerId: partner.id, reason: 'E2E fleet setup',
  });
  await adminPost(request, adminToken, '/api/admin/guarantees', {
    vehicleId, startsAt, endsAt, reason: 'E2E guarantee',
  });
}

async function adminPost(request: APIRequestContext, token: string, url: string, data: unknown): Promise<Record<string, any>> {
  const csrf = (await (await request.get('/api/auth/csrf')).json()).token as string;
  const response = await request.fetch(url, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'X-XSRF-TOKEN': csrf }, data });
  if (!response.ok()) throw new Error(`POST ${url}: ${response.status()} ${await response.text()}`);
  return await response.json();
}
