import { test, expect } from '@playwright/test';

// Admin should access admin invoices; customer should be forbidden

test('admin can access admin invoices and download pdf; customer forbidden', async ({ page, request }) => {
  // admin login (adjust credentials as needed)
  await page.goto('/auth/login');
  await page.fill('input[name="email"]','admin@example.com');
  await page.fill('input[name="password"]','password');
  await page.click('button[type=submit]');
  await page.waitForURL('/admin');
  await page.goto('/admin/invoices');
  await expect(page.locator('h1')).toContainText('Factures');
  const btn = page.locator('button', { hasText: 'PDF' }).first();
  if (await btn.count() > 0) {
    await btn.click();
    const resp = await page.waitForResponse(r => r.url().includes('/api/admin/invoices/') && r.url().endsWith('/pdf') && r.status() === 200);
    expect(resp.headers()['content-type']).toContain('application/pdf');
  }

  // now customer cannot access admin invoices
  await page.goto('/auth/login');
  await page.fill('input[name="email"]','test.customer@example.com');
  await page.fill('input[name="password"]','password');
  await page.click('button[type=submit]');
  await page.waitForURL('/customer/dashboard');
  await page.goto('/admin/invoices');
  // expect redirect or forbidden message
  await expect(page).toHaveURL(/forbidden|auth|login|customer/);
});
