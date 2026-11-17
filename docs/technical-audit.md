# Elite Tunisia — Technical Audit

> Scope: read-only audit. No application source, migration, configuration, or production data was modified.
> Evidence convention: **Verified** = read in source or executed in this audit. **Hypothesis** = inferred, not verified.

---

## 1. Executive summary

Elite Tunisia is a Spring Boot 4 / Angular / PostgreSQL modular monolith. The core transactional design is sound: booking overlap is protected by a PostgreSQL exclusion constraint plus pessimistic locks, quotes and payments are versioned and idempotent, and RBAC is applied at controller level. The backend unit/integration suite passes on H2.

Main gaps:

- Invoice flow is incomplete: the only invoice created in production code is the return-charge invoice, and only when extra charges are greater than zero. `createFromQuote` and `attachPayment` have no callers.
- Two concurrency and error-handling defects in the pickup/return path (lock-order inversion, silently swallowed exceptions).
- Payment idempotency and error handling need hardening; no refund path exists.
- Frontend tests and build cannot run on the installed Node v18.20.8; the frontend is unverified by execution.
- Several admin and public pages are placeholders.

**Classification**

| Component | Status |
|---|---|
| Booking availability and overlap prevention | Production-ready with one caveat (lock order, see ISS-01) |
| Booking lifecycle (create, confirm, cancel) | Production-ready |
| Pickup / return operations | Needs improvement (ISS-01, ISS-02, ISS-06) |
| Quotes | Needs improvement (ISS-09) |
| Payments | Needs improvement (ISS-03, ISS-04) |
| Invoices | Incomplete (ISS-04, ISS-05) |
| Driver assignment | Needs improvement (ISS-08) |
| Authentication and authorization (backend) | Production-ready with notes (ISS-11) |
| Database migrations | Needs improvement (ISS-07, ISS-05, ISS-06) |
| Frontend | Incomplete (placeholder pages) and unverified (ISS-10) |
| Testing | Backend verified; frontend blocked; Postgres-gated tests skipped |

**Production blockers:** none confirmed by execution. Before a real production launch, ISS-01, ISS-03 and ISS-04 should be treated as blockers.

---

## 2. Architecture

- Modular monolith under `tn.elitetunisia` (auth, booking, quote, payment, invoice, driver, transfer, vehicle, brand, category, customer, user, employee, common, catalogue, notification, report, document, partner, service). **Verified** by directory listing.
- Stateless JWT access token (15 min) plus refresh token rotation with family revocation; refresh tokens stored as SHA-256 hashes ([AuthService.java](backend/src/main/java/tn/elitetunisia/auth/service/AuthService.java)). **Verified.**
- Access token is kept in memory in the frontend ([auth.service.ts](frontend/src/app/core/auth/auth.service.ts)); CSRF uses a cookie with an `X-XSRF-TOKEN` header. **Verified.**
- Notification service interface has no real implementation; payment provider is simulated (per [target architecture study](docs/architecture/international-benchmark-and-target-architecture.md)). **Verified by that document; not re-checked in this audit.**

---

## 3. Backend

### 3.1 Configuration

- [application.yml](backend/src/main/resources/application.yml): `ddl-auto: validate` (good), Flyway enabled from `classpath:db/migration`, `JWT_SECRET` has no default (good), `include-stacktrace: never`. **Verified.**
- [application.properties](backend/src/main/resources/application.properties) contains only `spring.application.name`. Harmless, but duplicates config sources. Low.
- [application-prod.yml](backend/src/main/resources/application-prod.yml) enables secure cookies and disables Swagger and API docs. **Verified.**
- JWT secret must be at least 32 bytes ([TokenService.java](backend/src/main/java/tn/elitetunisia/auth/security/TokenService.java)). **Verified.**

### 3.2 Security configuration

- Stateless session, CSRF cookie repository, public endpoints limited to read-only catalogue endpoints, quote preview, policy, and auth endpoints. **Verified** in [SecurityConfig.java](backend/src/main/java/tn/elitetunisia/common/config/SecurityConfig.java).
- Customer controllers use `hasRole('CUSTOMER')` plus a granular authority (for example [CustomerBookingController.java](backend/src/main/java/tn/elitetunisia/booking/CustomerBookingController.java), [CustomerInvoiceController.java](backend/src/main/java/tn/elitetunisia/invoice/CustomerInvoiceController.java), [CustomerQuoteController.java](backend/src/main/java/tn/elitetunisia/quote/CustomerQuoteController.java)). **Verified.**
- Customer ownership is enforced by resolving the current account and filtering by its id (booking list, booking get, invoice get returns 404 for foreign invoices). **Verified.**

### 3.3 Backend API vs frontend calls

All endpoints called by [booking-api.service.ts](frontend/src/app/core/services/booking-api.service.ts) exist with matching verbs and paths (availability, quote-preview, policy, bookings CRUD, admin transitions, operations, pickup, return). **Verified by controller mapping grep.**

### 3.4 Backend tests

Executed in this audit: `backend\mvnw.cmd test` with JDK 21.0.2 (session-only `JAVA_HOME` override; the machine default `JAVA_HOME` points to JDK 8, which is a local environment issue):

- Tests run: 164, Failures: 0, Errors: 0, Skipped: 10. **BUILD SUCCESS.**
- Skipped: `InvoicePostgresIntegrationTests` (2), `PaymentPostgresIntegrationTests` (5), `QuoteEngineTests` (1). These are gated and were not executed against PostgreSQL.

---

## 4. Frontend

- Routing: public, auth, customer (guarded by `authGuard` + `roleGuard` with role `CUSTOMER`), admin (guarded by roles `SUPER_ADMIN`, `MANAGER`, `AGENT`, `CONCIERGE`, `ACCOUNTANT`, with stricter sub-route guards on customers, drivers, users). **Verified** in [app.routes.ts](frontend/src/app/app.routes.ts).
- Client-side role checks are UX only; backend authorization is the enforcement point. **Verified** by reading guard and controller annotations.
- Placeholder pages: `/admin` overview, `/admin/payments`, `/admin/partners`, `/admin/reports`, `/customer/notifications`, `/chauffeur`, `/airport-transfer`, `/services`, `/about`, `/faq`, `/contact`, `/auth/forgot-password`. **Verified** (routes use a generic placeholder component).
- Auth interceptor fetches a fresh CSRF token before every mutating request, adding one round trip per mutation. Low. **Verified** in [auth.interceptor.ts](frontend/src/app/core/interceptors/auth.interceptor.ts).
- Error interceptor shows backend `detail` text directly for 4xx responses other than 401/403, which can expose English backend messages to French users. Low. **Verified** in [error.interceptor.ts](frontend/src/app/core/interceptors/error.interceptor.ts).
- Frontend build and unit tests **not executed**: Node v18.20.8 is below the Angular CLI minimum (v22.22.3, v24.15.0 or v26.0.0). **Verified by execution attempt.** Playwright E2E specs exist in [frontend/e2e](frontend/e2e) and [frontend/tests/playwright](frontend/tests/playwright) but were not run.

---

## 5. Database (Flyway)

### 5.1 Migration inventory

- Source of truth: root [database/migrations](database/migrations) V1 to V20. The pom copies this folder to `db/migration` on the classpath. **Verified** in [pom.xml](backend/pom.xml).
- Sequences used by code exist: `booking_reference_seq` (V4), `quote_reference_seq` (V5), `invoice_seq` (V7). **Verified.**
- V4 (booking engine): exclusion constraint `bookings_no_blocking_overlap` on `tstzrange(starts_at, ends_at)` for CONFIRMED and IN_PROGRESS. Status and amount CHECKs. **Verified.**
- V5 (quotes): quote totals CHECK, `ends_at > starts_at` CHECK. **Verified.**
- V6 (payments): renames the old table to `legacy_payments`; new `payments` with global unique idempotency key index; FK to quotes CASCADE, to bookings SET NULL; no amount or status CHECK. **Verified.**
- V7 (invoices): renames the old table to `legacy_invoices`; `invoice_seq`; reference unique; no uniqueness on quote or booking; no total CHECK. **Verified.**
- V8 (booking operations): `action` column unconstrained; no odometer or fuel CHECKs; no uniqueness per booking and action. **Verified.**
- V11 (driver assignments): CHECK `starts_at < ends_at`, FKs, indexes. **No exclusion constraint** for overlapping assignments. **Verified.**
- V17: alters nullability of `payments.refunded_amount` to NOT NULL. **Verified by grep.**
- Not read individually in this audit: V1, V2, V3, V9, V10, V12 to V16, V18 to V20. Hypothesis: no conflict; not verified.

### 5.2 Stale migration artifacts

- [backend/src/main/resources/db/migration/V11__create_booking_driver_assignments.sql](backend/src/main/resources/db/migration/V11__create_booking_driver_assignments.sql) duplicates a root migration. The pom excludes `db/migration/**` from `src/main/resources`, so it is not packaged. Still a source of confusion. Medium.
- `_DISABLED_V4__add_driver_fields.sql`, `_DISABLED_V5__payments_idempotency_unique.sql`, `_DISABLED_V6__invoices_sequence_and_constraints.sql` exist in the same folder. They are not packaged, but the content of V5/V6 "idempotency" and "invoice sequence" fixes is not reflected in the active migrations as a separate step. Medium.
- No destructive statements (DROP TABLE, TRUNCATE, DELETE) were found in root migrations except the table renames in V6 and V7 (non-destructive, legacy kept). **Verified by grep.**

---

## 6. Business logic

### 6.1 Vehicle availability and overlap prevention

- Availability check and booking creation lock the vehicle (`vehicles.findLocked`), then check overlap, then insert. **Verified** in [BookingService.java](backend/src/main/java/tn/elitetunisia/booking/BookingService.java).
- PostgreSQL exclusion constraint enforces overlap for CONFIRMED and IN_PROGRESS as a second line of defence. **Verified** in V4.
- Overlap does not block PENDING bookings by design (`pendingBlocks` in policy). Hypothesis: intended; confirm with business owner.

### 6.2 Booking lifecycle

- Transitions: PENDING to CONFIRMED, REJECTED, CANCELLED; CONFIRMED to IN_PROGRESS, CANCELLED; IN_PROGRESS to COMPLETED. **Verified** in [BookingStatus.java](backend/src/main/java/tn/elitetunisia/booking/BookingStatus.java).
- Admin start and complete go through `transition()`, which requires pickup and return operations. **Verified** earlier in the audit.
- `recordReturn` sets COMPLETED directly, bypassing `BookingService` transition rules. Medium (ISS-02).

### 6.3 Pickup and return

- `recordPickup` and `recordReturn` lock the booking first, then the vehicle. `BookingService.locked` locks the vehicle first, then the booking. **Lock-order inversion**, deadlock risk under concurrent operations on the same booking and vehicle. **Verified by reading both methods.** (ISS-01)
- Audit writes are wrapped in `try { ... } catch (Exception ignore) {}`. A failed audit write is silently ignored, and in a transaction a failed insert can mark the transaction rollback-only. **Verified.** (ISS-02)

### 6.4 Quotes

- Quote lock uses the booking pessimistic lock. `QuoteService.get()` (read path) can expire quotes and writes state. **Verified.** (ISS-09)

### 6.5 Payments

- `createForQuote`: locks the quote, checks ownership and ACCEPTED status, checks amount equals quote total, looks up existing payment by idempotency key globally, `saveAndFlush`, catches `DataIntegrityViolationException` inside the same `@Transactional` method. **Verified.** Catching inside a transaction that already hit a constraint violation leads to rollback-only and `UnexpectedRollbackException` risk. (ISS-03)
- Provider call (`provider.process`) runs synchronously inside the database transaction. Holding a DB transaction and locks across an external call increases lock time. Medium. (ISS-03)
- Idempotency key is unique globally, not scoped per customer or quote. A key collision across customers returns or rejects another customer's payment. Medium. (ISS-03)
- No refund flow for REFUNDED status. Medium (incomplete).

### 6.6 Invoices

- `createReturnChargeInvoice`: only wired invoice creation, invoked from `BookingOperationService`. **Verified.**
- `createFromQuote`: no callers in main code. Used by tests only. **Verified by grep.** Customers cannot receive a rental invoice in the current product. High (incomplete).
- `attachPayment`: no callers; `on conflict do nothing` with a catch-all fallback; no customer consistency check. **Verified.** (ISS-04)
- `createFromQuote` fallback: on `invoice_seq` failure, it uses `System.currentTimeMillis() % 1000000` as a number, which can collide and hides the error. **Verified.** (ISS-05)
- `adminList()` returns all invoices without pagination. Medium. **Verified.**
- Customer invoice access returns 404 for unknown and foreign invoices. **Verified.**

### 6.7 Driver assignment

- `assignDriver` locks the booking and the driver row, validates status, active flag, license expiry, certification; then checks overlap with `assignments.overlaps` and deletes or saves the assignment. **Verified** in [DriverAssignmentService.java](backend/src/main/java/tn/elitetunisia/driver/DriverAssignmentService.java).
- Because the driver row is locked, concurrent assignments for the same driver are serialized in this code path. Overlap is therefore protected at application level only. No database exclusion constraint exists (V11). Medium (ISS-08). Priority rises to High if another write path to `booking_driver_assignments` exists.

### 6.8 Authentication and authorization

- Login: throttle keyed by email and IP, failure audit, refresh rotation, replay detection revokes the family, logout revokes. **Verified** in [AuthService.java](backend/src/main/java/tn/elitetunisia/auth/service/AuthService.java).
- Access tokens are not revoked before expiry (15 min); disabled users are only blocked on refresh. Low (ISS-11). Hypothesis: JWT filter checks user status; not verified.
- LoginThrottle implementation (in-memory vs shared) was not read; multi-instance behaviour is unverified. Medium (testing gap).

---

## 7. Testing

| Area | Result | Evidence |
|---|---|---|
| Backend unit and integration (H2) | Passed: 164 run, 0 failed, 10 skipped | Executed in this audit |
| Backend Postgres integration (invoice, payment) | Skipped (gated) | Not executed |
| Backend quote engine test | Skipped (1) | Not executed |
| Frontend unit tests | Not executed | Node v18.20.8 below Angular CLI minimum |
| Frontend build | Not executed | Same Node version blocker |
| Playwright E2E (frontend/e2e, frontend/tests/playwright) | Not executed | Requires running frontend and Node upgrade |

Prior run documentation in [docs/TEST_RUN_RESULT_BACKEND.md](docs/TEST_RUN_RESULT_BACKEND.md) is historical and not used as evidence here.

---

## 8. Issue register

| ID | Description | Affected files | Severity | Impact | Recommendation | Dependencies | Priority |
|---|---|---|---|---|---|---|---|
| ISS-01 | Lock-order inversion between pickup/return (booking then vehicle) and `BookingService.locked` (vehicle then booking) | [BookingOperationService.java](backend/src/main/java/tn/elitetunisia/booking/BookingOperationService.java), [BookingService.java](backend/src/main/java/tn/elitetunisia/booking/BookingService.java) | High | Deadlock under concurrent operations on the same booking/vehicle | Use one global lock order (vehicle then booking) in all paths; add a concurrency test | ISS-02 | P1 |
| ISS-02 | Audit writes wrapped in `catch (Exception ignore)`; `recordReturn` bypasses transition rules | BookingOperationService.java | High | Silent audit loss; rollback-only risk; status changes outside rules | Remove silent catch; propagate or record a failure; route COMPLETED through `BookingService` | None | P1 |
| ISS-03 | Payment idempotency: global key, catch inside transaction, provider call inside DB transaction, no refund flow | [PaymentService.java](backend/src/main/java/tn/elitetunisia/payment/PaymentService.java) | High | Cross-customer key collision; `UnexpectedRollbackException`; long lock time; no refund | Scope key per customer and quote; move the duplicate-key catch to a separate transaction; move provider call outside lock or use an outbox; design refund | None | P1 |
| ISS-04 | Invoice-from-quote and attach-payment flows have no callers | [InvoiceService.java](backend/src/main/java/tn/elitetunisia/invoice/InvoiceService.java) | High (incomplete) | Customers never receive a rental invoice | Decide product behaviour, then wire `createFromQuote` on payment success; add integration test | Business decision on invoice trigger | P2 |
| ISS-05 | Invoice numbering fallback uses time modulo; catch-all hides `invoice_seq` errors; no uniqueness per quote/booking; no total CHECK; unpaginated admin list | InvoiceService.java, V7 migration | Medium | Duplicate invoice numbers possible; silent failures; large list responses | Remove fallback (fail fast); add CHECK constraints and unique per source in a new migration; paginate | ISS-04 | P2 |
| ISS-06 | `booking_operations` lacks action CHECK, PICKUP/RETURN uniqueness, odometer and fuel bounds | V8 migration | Medium | Invalid or duplicate operations possible if bypassing app | New migration adding CHECK and partial unique index | None | P2 |
| ISS-07 | Stale migration artifacts: duplicate V11 in resources, three `_DISABLED_` files | backend/src/main/resources/db/migration | Medium | Confusion during migration review; risk if pom changes | Archive or delete in a separate, reviewed change; document | None | P3 |
| ISS-08 | Driver overlap enforced only by application check (serialized by driver row lock); no DB exclusion constraint | [DriverAssignmentService.java](backend/src/main/java/tn/elitetunisia/driver/DriverAssignmentService.java), V11 | Medium (High if other write paths exist) | Overlapping driver assignments if a new write path bypasses the lock | Add exclusion constraint in a new migration (after cleaning existing overlaps) | Data check first | P2 |
| ISS-09 | Quote read path writes state and takes booking lock | [QuoteService.java](backend/src/main/java/tn/elitetunisia/quote/QuoteService.java) | Low/Medium | Read contention; surprising side effects on GET | Move expiry to scheduled job or write-free read | None | P3 |
| ISS-10 | Frontend build and unit tests blocked by Node v18; Playwright E2E not run | frontend (environment) | Medium (testing gap) | No evidence for frontend correctness | Upgrade Node to v22.22.3+ and rerun build, unit tests, E2E | None | P1 (verification) |
| ISS-11 | Login throttle implementation and access-token revocation not verified | [AuthService.java](backend/src/main/java/tn/elitetunisia/auth/service/AuthService.java), LoginThrottle | Medium (unverified) | Brute-force protection may not hold across instances | Read LoginThrottle and JWT filter; confirm shared store if multi-instance | None | P2 |
| ISS-12 | Placeholder admin and public pages (payments, partners, reports, dashboards, notifications) | [app.routes.ts](frontend/src/app/app.routes.ts) | Medium (incomplete) | Product scope gap | Prioritise per roadmap | Product decision | P3 |
| ISS-13 | Frontend shows raw backend 4xx detail; CSRF fetch per mutation | error.interceptor.ts, auth.interceptor.ts | Low | Inconsistent messages; extra latency | Map error codes to French messages; cache CSRF token | None | P4 |
| ISS-14 | Postgres integration tests skipped in default run | backend tests | Medium (testing gap) | Concurrency and constraint behaviour not exercised | Run gated tests against an isolated Postgres (never production) | ISS-01, ISS-03 fixes | P1 (verification) |

---

## 9. Remediation roadmap

**Phase 1 (verify and stop the concurrency risks)**

1. Upgrade Node to v22.22.3+ and run the frontend build, unit tests, and E2E. (ISS-10)
2. Run Postgres-gated tests against an isolated local database. (ISS-14)
3. Fix lock order and audit swallowing in pickup/return. (ISS-01, ISS-02)
4. Fix payment idempotency scope and transaction handling; define refund behaviour. (ISS-03)

**Phase 2 (complete invoices and data integrity)**

5. Decide invoice trigger on payment; wire `createFromQuote` and `attachPayment`. (ISS-04)
6. Remove the numbering fallback; add invoice constraints and pagination. (ISS-05)
7. Add booking operation constraints and driver assignment exclusion constraint (after data check). (ISS-06, ISS-08)
8. Verify login throttle and token revocation behaviour. (ISS-11)

**Phase 3 (clean-up and product completion)**

9. Remove stale migration artifacts after review. (ISS-07)
10. Move quote expiry out of the read path. (ISS-09)
11. Replace placeholder pages per product roadmap. (ISS-12)
12. Improve frontend error messages and CSRF handling. (ISS-13)

---

## 10. Actions taken in this audit

- Read-only review of backend, database migrations, frontend routes, guards, interceptors, and services.
- Executed backend tests (164 run, 0 failed, 10 skipped).
- Attempted frontend build and tests; blocked by Node version.
- Created only this file: `docs/technical-audit.md`.
- No application source, migration, configuration, dependency, or database was modified.

Waiting for your approval before implementing any fix.
