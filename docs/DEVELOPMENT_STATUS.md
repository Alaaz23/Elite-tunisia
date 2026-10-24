Development status snapshot

- Backend: core booking/payment/invoice flows implemented and unit-tested (H2 profile).
- Frontend: Angular build succeeded earlier but unit/E2E tests blocked by Node version (see SKIPPED_TESTS_REPORT.md).
- Playwright: browser binaries installed but execution blocked by Node runtime mismatch.

Recent backend fixes applied:
- `InvoiceService.attachPayment`: require captured payments; make DB insert compatible with Postgres and H2 (fallback exists-check insert).

Next backend tasks:
- Run full backend test suite locally (`./mvnw.cmd test`).
- When Postgres available, run Postgres-gated integration tests to validate invoice sequence concurrency.
- Frontend: Angular sources present; runtime execution blocked by Node.js v18. Exact next steps and commands are in TEST_RUN_COMMANDS.md (created below).
