Frontend Angular + Playwright run instructions

Prerequisites
- Node.js >= 22.22.3 (or compatible: ^22.22.3 || ^24.15.0 || >=26)
- npm v10.9.4 (project uses `packageManager: "npm@10.9.4"`)
- (Optional for E2E) A running backend at the address the frontend will target (same origin by default) and Postgres for Postgres-gated ITs.

Typical local commands (run from workspace root `frontend/`):

# install dependencies
npm ci

# run unit tests (Angular)
npm test

# start dev server (for manual testing)
npm start

# build production bundle
npm run build -- --configuration production

# run Playwright E2E (requires backend running and compatible Node)
npm run e2e

Notes about API base URL
- The frontend uses an `InjectionToken` `API_BASE_URL` (default empty). When serving from the same origin as the backend, no extra config is required.
- To target a backend on a different host, provide the base URL at runtime (e.g., via environment replacement or a small script that sets the token).

PostgreSQL prerequisites for integration tests (backend)
- A reachable Postgres instance with a database for tests.
- Set environment variables before running Postgres-gated ITs:
  - `ELITE_POSTGRES_TEST=true`
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<db>`
  - `SPRING_DATASOURCE_USERNAME=<user>`
  - `SPRING_DATASOURCE_PASSWORD=<pass>`
- Ensure Flyway migrations are applied (maven test will apply them when datasource configured).
- Run Postgres ITs with mvn profile or env guard used in project (example):

# run backend integration tests requiring Postgres
call ..\run_mvn_with_jdk21_fix.bat && mvnw.cmd -DskipITs=false -Dtest=*IT test

Caveats
- Do NOT upgrade Node automatically in this workspace; perform Node upgrade outside this repo or in CI.
- Frontend E2E requires the backend API to be reachable and seeded with test data matching expected accounts and roles.
