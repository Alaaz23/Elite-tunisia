import { test, expect } from '@playwright/test';

// Basic scenario: customer views quote, pays, and downloads invoice PDF
// Relies on a running dev server and seeded test data. Adjust selectors if templates change.

test.describe('Quote -> Payment -> Invoice', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/auth/login');
    // assume test account exists; credentials may be configured via env in CI
    await page.fill('input[name="email"]', 'test.customer@example.com');
    await page.fill('input[name="password"]', 'password');
    await page.click('button[type="submit"]');
    await page.waitForURL('/customer/dashboard');
  });

  test('accept quote, make payment, download invoice', async ({ page }) => {
    // navigate to quotes list
    await page.goto('/customer/quotes');
    await expect(page.locator('h1')).toHaveText(/Devis|Quotes/);
    // open first quote
    await page.click('a.quote-link');
    await page.waitForSelector('.quote-page');

    // if quote is S E N T -> accept flow; else assume it's ACCEPTED and payable
    const payBtn = page.locator('button', { hasText: 'Payer' });
    if (await payBtn.count() === 0) {
      // try accept then pay
      const acceptBtn = page.locator('button', { hasText: 'Accepter' });
      if (await acceptBtn.count() > 0) {
        await acceptBtn.click();
        await page.waitForResponse(r => r.url().includes('/api/customer/quotes/') && r.status() === 200);
      }
    }

    await payBtn.click();
    // wait for payment request
    await page.waitForResponse(r => r.url().includes('/payments') && r.status() === 200);
    await expect(page.locator('.status')).toHaveText(/Paiement réussi|success/i);

    // navigate to invoices and download
    await page.goto('/customer/invoices');
    await page.waitForSelector('.invoices');
    const dl = page.locator('button', { hasText: 'Télécharger' }).first();
    await dl.click();
    // expect a fetch to /api/invoices/{id}/pdf
    const resp = await page.waitForResponse(r => r.url().includes('/api/invoices/') && r.url().endsWith('/pdf') && r.status() === 200);
    expect(resp.headers()['content-type']).toContain('application/pdf');
  });
});
