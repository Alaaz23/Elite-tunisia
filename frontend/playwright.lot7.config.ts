import { defineConfig, devices } from '@playwright/test';

// Parcours navigateur du Lot 7 : le serveur Angular doit déjà tourner (ng serve sur 127.0.0.1:4300).
// Les appels /api sont simulés dans chaque test ; aucun appel ne doit atteindre un backend réel.
export default defineConfig({
  testDir: './tests/browser-lot7',
  testMatch: '**/*.e2e.ts',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  reporter: [['list']],
  outputDir: './test-results-lot7',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.LOT7_BASE_URL ?? 'http://127.0.0.1:4300',
    trace: 'off',
    screenshot: 'only-on-failure',
    launchOptions: process.env.PLAYWRIGHT_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
      : {},
    ...devices['Desktop Chrome'],
  },
});
