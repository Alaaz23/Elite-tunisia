package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostgresTestGuardTest {

    private static final String USER = "elite_test_runner";
    private static final String PASSWORD = "not-a-real-secret";

    @Test
    void acceptsIsolatedClusterWithDedicatedTestDatabase() {
        PostgresTestGuard.Config config = PostgresTestGuard.validate(
                "jdbc:postgresql://127.0.0.1:15432/elite_a4_test", USER, PASSWORD);
        assertEquals("elite_a4_test", config.database());
        assertEquals(USER, config.username());
    }

    @Test
    void acceptsLocalhostOnPort15432() {
        PostgresTestGuard.validate("jdbc:postgresql://localhost:15432/elite_test", USER, PASSWORD);
    }

    @Test
    void refusesPort5432() {
        assertRefused("jdbc:postgresql://127.0.0.1:5432/elite_a4_test", USER, PASSWORD);
    }

    @Test
    void refusesMissingExplicitPort() {
        assertRefused("jdbc:postgresql://127.0.0.1/elite_a4_test", USER, PASSWORD);
    }

    @Test
    void refusesNonLocalHost() {
        assertRefused("jdbc:postgresql://10.0.0.5:15432/elite_a4_test", USER, PASSWORD);
    }

    @Test
    void refusesDatabaseNotDedicatedToTests() {
        assertRefused("jdbc:postgresql://127.0.0.1:15432/postgres", USER, PASSWORD);
        assertRefused("jdbc:postgresql://127.0.0.1:15432/et_dev_db", USER, PASSWORD);
        assertRefused("jdbc:postgresql://127.0.0.1:15432/et_test_db", USER, PASSWORD);
    }

    @Test
    void refusesUnresolvedPlaceholders() {
        assertRefused("${env.ELITE_POSTGRES_URL}", USER, PASSWORD);
        assertRefused("jdbc:postgresql://127.0.0.1:15432/elite_a4_test", "${env.ELITE_POSTGRES_USERNAME}", PASSWORD);
    }

    @Test
    void refusesIncompleteConfiguration() {
        assertRefused(null, USER, PASSWORD);
        assertRefused("jdbc:postgresql://127.0.0.1:15432/elite_a4_test", " ", PASSWORD);
        assertRefused("jdbc:postgresql://127.0.0.1:15432/elite_a4_test", USER, null);
    }

    @Test
    void refusesSuperuserRole() {
        assertRefused("jdbc:postgresql://127.0.0.1:15432/elite_a4_test", "postgres", PASSWORD);
    }

    private static void assertRefused(String url, String user, String password) {
        assertThrows(IllegalStateException.class, () -> PostgresTestGuard.validate(url, user, password));
    }
}
