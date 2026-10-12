package tn.elitetunisia;

import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresTestHelper {
    private PostgresTestHelper() {}

    public static void requirePostgresIfRequested(JdbcTemplate jdbc) throws Exception {
        String usePg = System.getenv("ELITE_POSTGRES_TEST");
        if (usePg != null && usePg.equalsIgnoreCase("true")) {
            try (var conn = jdbc.getDataSource().getConnection()) {
                String product = conn.getMetaData().getDatabaseProductName();
                if (product == null || !product.contains("Postgre")) {
                    throw new IllegalStateException("ELITE_POSTGRES_TEST requested but database is not PostgreSQL: " + product);
                }
            }
        } else {
            Assumptions.assumeTrue(false, "Postgres tests not requested");
        }
    }
}
