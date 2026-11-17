import { APIRequestContext, expect } from '@playwright/test';

// The backend confirms a booking only when its quote is ACCEPTED and a CAPTURED payment exists.
// These helpers drive that real chain through the API so scenarios can reach CONFIRMED.

export async function acceptAndPayQuote(
  request: APIRequestContext,
  adminToken: string,
  customerToken: string,
  bookingId: string,
  validUntil: string,
): Promise<Record<string, any>> {
  const quote = await send(request, adminToken, 'POST', `/api/admin/quotes/booking/${bookingId}`, {
    validUntil, withDriver: false, discountAmount: 0, customerNote: null, internalNote: null, services: [],
  });
  await send(request, adminToken, 'POST', `/api/admin/quotes/${quote.id}/send`, {});
  await send(request, customerToken, 'POST', `/api/customer/quotes/${quote.id}/accept`, {});
  await payQuoteInFull(request, adminToken, customerToken, quote.id as string);
  return quote;
}

export async function payQuoteInFull(
  request: APIRequestContext,
  adminToken: string,
  customerToken: string,
  quoteId: string,
): Promise<Record<string, any>> {
  const quote = await send(request, adminToken, 'GET', `/api/admin/quotes/${quoteId}`, undefined);
  const payment = await send(request, customerToken, 'POST', `/api/customer/quotes/${quoteId}/payments`, {
    amount: quote.totalAmount, method: 'CARD', idempotencyKey: `e2e-pay-${quoteId}`,
  });
  expect(payment.status).toBe('CAPTURED');
  return payment;
}

async function send(request: APIRequestContext, token: string, method: string, url: string, data: unknown): Promise<Record<string, any>> {
  const csrf = (await (await request.get('/api/auth/csrf')).json()).token as string;
  const response = await request.fetch(url, { method, headers: { Authorization: `Bearer ${token}`, 'X-XSRF-TOKEN': csrf }, data });
  expect(response.ok(), `${method} ${url}: ${response.status()} ${response.ok() ? '' : await response.text()}`).toBe(true);
  return await response.json();
}
