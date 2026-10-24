Skipped tests and causes

- Frontend unit tests (`npm test`) and Playwright E2E (`npm run e2e`) are skipped in this environment due to Node.js runtime incompatibility.
  - Present Node.js: 18.20.8
  - Required by project (package.json / Angular CLI): >= 22.22.3 (also Playwright requires >=20)
  - Action: upgrade Node.js to >=22.22.3 or run CI with a compatible image. Do not switch Node versions automatically in this workspace without authorization.

- Postgres-gated integration tests are skipped when no PostgreSQL test database is available. The test harness uses H2 fallbacks but cannot validate Postgres-specific behaviors like sequence concurrency and "on conflict" semantics.
  - Action: run integration tests in CI or local environment with Postgres accessible and migrations applied.
 
Additional notes:
 - Frontend code is present and calls APIs matching backend controllers for bookings, quotes, invoices, catalogue and drivers. Running frontend tests/build/E2E is blocked by local Node.js (v18). See `docs/TEST_RUN_COMMANDS.md` for exact commands and prerequisites.
 - Payment flow: frontend posts payments to `/api/customer/quotes/{quoteId}/payments` with a required `idempotencyKey`; backend controller expects the same payload and enforces idempotence.
