import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4200',
    trace: 'retain-on-failure',
    launchOptions: process.env.PLAYWRIGHT_EXECUTABLE_PATH
      ? { executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH }
      : {},
    ...devices['Desktop Chrome'],
  },
  expect: { timeout: 8_000 },
  // Mesuré : les parcours multi-utilisateurs réussissent en 13 à 28 s sous `ng serve` (API < 0,7 s, le temps vient du
  // chargement des chunks et du rendu) ; 30 s était trop proche de la durée réelle. Les attentes `expect` restent à 8 s.
  timeout: 60_000,
});