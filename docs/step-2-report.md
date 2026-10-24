# Step 2 — Technical Skeleton Report

| Area | Status | Evidence |
| --- | --- | --- |
| Frontend | PASS | Angular production build succeeds; 2 unit tests pass; mobile menu and `/vehicles` route verified at 375 px without horizontal overflow. The homepage component stylesheet reports a 5.03 kB budget warning. |
| Backend | PASS | Spring Boot 4.1.1 compiles on Java 21; 4 integration tests pass on H2. |
| Database | FAIL | Flyway migration is configured and packaged, but could not be applied to PostgreSQL: the local service rejects the documented development credentials. |
| Docker | FAIL | `docker-compose.yml` is present, but Docker is not installed in this environment; PostgreSQL Compose startup could not be run. |
| Authentication skeleton | PASS | BCrypt registration/login, short-lived JWT, rotating/revocable opaque refresh token, HttpOnly cookie, and protected admin matcher are implemented and tested on H2. |
| Tests | PASS | Backend 4/4 and frontend 2/2 pass. No automated Playwright/Cypress E2E suite exists yet. |
| Documentation | PASS | README and architecture, database, API, security, testing, and step report documents are present. |

## Remaining Issues

- Install Docker Desktop or provide a PostgreSQL instance and development credentials, then run the Flyway migration and verify `/api/health` against PostgreSQL.
- The current machine exposes Node 18 on its normal PATH and a stale Java 8 `JAVA_HOME`; Angular was built with temporary Node 24 and Maven was run with `JAVA_HOME` explicitly pointed at Java 21. Correct these local toolchain settings for normal commands.
- Frontend login/register screens, vehicle catalogue, customer/admin workflows, and all business-domain screens remain placeholders or unimplemented.
- The database migration has not been exercised against PostgreSQL; H2 does not prove PostgreSQL migration compatibility.
- Fine-grained backend permissions, rate limiting, email verification, password recovery, and a deployment-specific CSRF decision remain outstanding.
- Payment, file storage, outbound email/SMS, legal content, backups, monitoring, deployment, and production secrets management are not configured.
- No booking availability transaction/overlap constraint, price calculation service, or business CRUD has been implemented.
- The responsive check covered the 375 px viewport and primary menu/route only; tablet and desktop visual QA remains outstanding.

## Production Readiness

NOT READY FOR PRODUCTION. This repository is a compilable technical foundation only; database provisioning and all business, legal, operational, and broader security work must be completed before launch.