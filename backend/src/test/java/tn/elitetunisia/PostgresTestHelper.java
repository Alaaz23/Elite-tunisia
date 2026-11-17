package tn.elitetunisia;

import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Accès PostgreSQL pour les tests Spring marqués ELITE_POSTGRES_TEST.
 *
 * <p>La configuration vient exclusivement de ELITE_POSTGRES_URL, ELITE_POSTGRES_USERNAME et
 * ELITE_POSTGRES_PASSWORD, validées par {@link PostgresTestGuard}. Le schéma est construit uniquement
 * par les migrations Flyway (ddl-auto=validate). Le helper ne crée, ne migre et ne supprime aucune base.
 */
public final class PostgresTestHelper {
    private static PostgresTestGuard.Config config;

    private PostgresTestHelper() {}

    public static synchronized boolean configurePostgresIfRequested() {
        if (!"true".equalsIgnoreCase(System.getenv("ELITE_POSTGRES_TEST"))) {
            return false;
        }
        if (config == null) {
            config = PostgresTestGuard.validate(
                    System.getenv(PostgresTestGuard.URL_VAR),
                    System.getenv(PostgresTestGuard.USER_VAR),
                    System.getenv(PostgresTestGuard.PASSWORD_VAR));
        }

        System.setProperty("spring.datasource.url", config.url());
        System.setProperty("spring.datasource.username", config.username());
        System.setProperty("spring.datasource.password", config.password());
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.flyway.locations", "classpath:db/migration");
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
        if (!configurePostgresIfRequested()) {
            Assumptions.assumeTrue(false, "Postgres tests not requested");
            return;
        }
        try (var conn = jdbc.getDataSource().getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null || !product.contains("Postgre")) {
                throw new IllegalStateException("ELITE_POSTGRES_TEST requested but database is not PostgreSQL: " + product);
            }
            try (var statement = conn.createStatement();
                    var result = statement.executeQuery("SELECT current_database(), current_user")) {
                if (!result.next() || !config.database().equals(result.getString(1))
                        || !config.username().equals(result.getString(2))) {
                    throw new IllegalStateException(
                            "PostgreSQL integration tests require the database " + config.database()
                                    + " and the configured test user.");
                }
            }
        }
    }
}
