Postgres test environment

Start:

1. Ensure Docker is installed and running.
2. From `backend` folder run:

```bash
docker compose -f docker/docker-compose.postgres.test.yml up -d
```

This starts a Postgres 15 instance on port 15432 with database `et_test_db`, user `et_test` and password `et_test`.

Stop & cleanup:

```bash
docker compose -f docker/docker-compose.postgres.test.yml down
```

To remove data volume (local test data):

```bash
docker volume rm et_postgres_test_data
```

Running integration tests

Integration tests that require Postgres are guarded by an environment variable. Example run:

```bash
# start DB first
docker compose -f docker/docker-compose.postgres.test.yml up -d
# run tests (from backend)
mvnw.cmd -Dtest=*Postgres* -Denable.postgres.it=true test
```

Note: Do NOT run these commands against production databases. The compose file uses isolated port and volume names.
