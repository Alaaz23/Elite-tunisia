# DEVELOPMENT STATUS

- Tests: H2 unit suite green (55 tests run, 0 failures, 0 errors, 10 skipped - Postgres-gated). See SKIPPED_TESTS_REPORT.md for details.
- Last action: Restored MockMvc/Security tests for admin driver assignment; MockMvc coverage checked and passing.
 - Next: Inspect `cahier-des-charges.md` and begin implementing missing features in order: bookings/availability, quotes, payments, invoices, vehicles/drivers/transfers, pickup/return, Angular integration.
 - Frontend status: Blocked — local Node is `v18.20.8`, but `frontend/package.json` requires `^22.22.3 || ^24.15.0 || >=26`. No compatible Node found on PATH or common version managers.
	 - Prepared commands to run when Node is upgraded are in `docs/TEST_RUN_COMMANDS.md`.
- Bookings/Availability: VERIFIED on H2. Focused tests (`BookingEngineTests`) pass (8 tests, 0 failures, 2 skipped - Postgres-gated concurrency). Customer isolation, date validation, availability checks, status transitions and admin confirmations are implemented and covered by unit and MockMvc tests. No code changes required for this lot.
 - Actions taken: ran focused booking tests; added MockMvc security test for admin driver assignment and created skipped-tests report.
 - Remaining: Postgres-only concurrency tests need a real Postgres test DB to validate advisory locks and exclusion semantics (environment: ELITE_POSTGRES_TEST=true).

Next: Lot 2 — Quotes: VERIFIED on H2. Quote versioning, snapshots, send/accept/reject flows and price snapshotting are covered by `QuoteEngineTests`. One Postgres-gated concurrency test remains skipped.

Lot 3 — Payments: VERIFIED on H2 (unit tests). `PaymentService` enforces idempotency, owner checks and server-side amount verification. Added migration V5 to make `payments.idempotency_key` unique and service handling for concurrent inserts. Integration tests requiring Postgres are gated and skipped.

Lot 4 — Invoices/PDF: Invoice creation implemented. Added migration V6 to create `invoice_seq`, unique constraint on `invoices.reference`, and `invoice_payments` join table. `InvoiceService.createFromQuote` uses DB sequence for unique, concurrent-safe references and snapshots quote lines into immutable invoice lines. Unit tests for PDF generation exist; Postgres-gated integration tests that assert reference uniqueness remain skipped.

Proceeding to Lot 5 — Vehicles, Drivers, Transfers and assignment flows. I inspected controller/service code and tests, added authorization enforcement to `AdminDriverController` (requires `drivers:manage` plus existing `admin:access`), and added `AdminDriverControllerMockMvcTest` to verify authorization and CSRF handling. Driver assignment and transfer unit tests pass on H2.

Lot 5 update: Added operational fields to `DriverEntity` and created Flyway migration `V4__add_driver_fields.sql` to persist license and certification metadata. Service APIs remain compatible; tests will be extended to exercise these fields in the next cycle.
