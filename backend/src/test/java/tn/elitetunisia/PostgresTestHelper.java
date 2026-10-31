package tn.elitetunisia;

import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresTestHelper {
    private PostgresTestHelper() {}

    public static boolean configurePostgresIfRequested() {
        if (!"true".equalsIgnoreCase(System.getenv("ELITE_POSTGRES_TEST"))) {
            return false;
        }

        String url = requiredEnvironmentVariable("DATABASE_URL");
        String username = requiredEnvironmentVariable("DATABASE_USERNAME");
        String password = requiredEnvironmentVariable("DATABASE_PASSWORD");
        boolean testEndpoint = url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):15432/et_test_db(?:\\?.*)?")
                && "et_test".equals(username);
        if (!testEndpoint) {
            throw new IllegalStateException(
                    "PostgreSQL tests only accept the isolated loopback test endpoint "
                            + "localhost:15432/et_test_db with user et_test.");
        }

        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.datasource.username", username);
        System.setProperty("spring.datasource.password", password);
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        System.setProperty("spring.jpa.defer-datasource-initialization", "false");
        // Chaque contexte Spring mis en cache garde son pool : sans borne, la suite dépasse max_connections (100 par défaut).
        System.setProperty("spring.datasource.hikari.maximum-pool-size", "5");
        System.setProperty("spring.datasource.hikari.minimum-idle", "1");
        System.setProperty("spring.sql.init.mode", "never");
        // application-test.yml impose H2Dialect ; la validation Hibernate doit utiliser le dialecte PostgreSQL.
        System.setProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        if (System.getProperty("app.security.jwt.secret") == null) {
            byte[] random = new byte[48];
            new java.security.SecureRandom().nextBytes(random);
            System.setProperty("app.security.jwt.secret", java.util.Base64.getEncoder().encodeToString(random));
        }
        System.clearProperty("spring.profiles.active");
        return true;
    }

    public static void requirePostgresIfRequested(JdbcTemplate jdbc) throws Exception {
        String usePg = System.getenv("ELITE_POSTGRES_TEST");
        if (usePg != null && usePg.equalsIgnoreCase("true")) {
            configurePostgresIfRequested();
            try (var conn = jdbc.getDataSource().getConnection()) {
                String product = conn.getMetaData().getDatabaseProductName();
                if (product == null || !product.contains("Postgre")) {
                    throw new IllegalStateException("ELITE_POSTGRES_TEST requested but database is not PostgreSQL: " + product);
                }
                try (var statement = conn.createStatement();
                        var result = statement.executeQuery("SELECT current_database(), current_user")) {
                    if (!result.next() || !"et_test_db".equals(result.getString(1))
                            || !"et_test".equals(result.getString(2))) {
                        throw new IllegalStateException(
                                "PostgreSQL integration tests require database et_test_db and user et_test.");
                    }
                }
            }
        } else {
            Assumptions.assumeTrue(false, "Postgres tests not requested");
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be explicitly set for PostgreSQL integration tests.");
        }
        return value;
    }
}
