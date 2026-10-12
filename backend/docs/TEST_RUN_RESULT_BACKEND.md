# Backend Test Run Result

Last run (JDK21, H2 in-memory, integration tests skipped):

- Command: `call run_mvn_with_jdk21_fix.bat && mvnw.cmd -DskipITs test`
- Result: BUILD SUCCESS
- Tests run: 81
- Failures: 0
- Errors: 0
- Skipped: 10 (Postgres-gated)

Notes:
- New tests added and passing: rental pricing boundary test, payment idempotence compatible tests, invoice attach-payment unit test.
- Postgres integration tests remain gated and documented in `docs/SKIPPED_TESTS_REPORT.md`.
