package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Lot 6 sur PostgreSQL isolé (migrations V1 à V26) : un seul paiement CAPTURED par devis, statuts de paiement
 * non capturés acceptés en plusieurs exemplaires, course à deux connexions, historique de paiement préservé
 * face à une suppression de devis, rollback des scénarios.
 *
 * <p>Activation : ELITE_A4_PG_TEST=true, ELITE_A4_PG_URL (jdbc:postgresql://127.0.0.1:15432/postgres),
 * ELITE_A4_PG_USER, ELITE_A4_PG_PASSWORD. Crée une base temporaire {@code et_a4_it_*} et la supprime.
 */
class PaymentBookingPostgresTest {

    private static final String ENABLED_FLAG = "ELITE_A4_PG_TEST";
    private static final String BASE_PREFIX = "et_a4_it_";
    private static final String MIGRATIONS = "filesystem:../database/migrations";

    private static String testDbName;
    private static String jdbcUrl;
    private static String adminUrl;
    private static String user;
    private static String password;

    @BeforeAll
    static void setUp() throws SQLException {
        assumeTrue("true".equalsIgnoreCase(System.getenv(ENABLED_FLAG)),
                "Test lot 6 désactivé : définir " + ENABLED_FLAG + "=true");
        adminUrl = System.getenv("ELITE_A4_PG_URL");
        user = System.getenv("ELITE_A4_PG_USER");
        password = System.getenv("ELITE_A4_PG_PASSWORD");
        assumeTrue(adminUrl != null && user != null && password != null, "Variables ELITE_A4_PG_* manquantes");
        assertTargetAllowed(adminUrl);

        testDbName = BASE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
                Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + testDbName);
        }
        jdbcUrl = adminUrl.replaceFirst("/[^/?]*(\\?|$)", "/" + testDbName + "$1");
        assertTargetAllowed(jdbcUrl);

        Flyway.configure()
                .dataSource(jdbcUrl, user, password)
                .locations(MIGRATIONS)
                .load()
                .migrate();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (testDbName == null) {
            return;
        }
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
                Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + testDbName + " WITH (FORCE)");
        }
    }

    static void assertTargetAllowed(String url) {
        assertTrue(url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):15432/.*"),
                "Cible refusée (hôte/port non autorisé)");
        String db = url.replaceFirst("^.*:15432/", "").replaceFirst("[?].*$", "");
        assertTrue(db.equals("postgres") || db.startsWith(BASE_PREFIX), "Base refusée : " + db);
        assertTrue(!db.equals("et_test_db") && !db.equals("et_dev_db"), "Base réservée refusée");
    }

    private Connection tx() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl, user, password);
        c.setAutoCommit(false);
        return c;
    }

    @Test
    void capturedPaymentIsUniquePerQuoteWhileOtherStatusesAreAccepted() throws SQLException {
        UUID quote;
        try (Connection c = tx()) {
            quote = insertFixtureQuote(c);
            for (String status : List.of("PENDING", "AUTHORIZED", "FAILED", "REFUNDED", "CANCELLED")) {
                insertPayment(c, quote, status, "k1-" + status + "-" + UUID.randomUUID());
                insertPayment(c, quote, status, "k2-" + status + "-" + UUID.randomUUID());
            }
            assertEquals(10, scalarInt(c, "select count(*) from payments where quote_id = '" + quote + "'"),
                    "les statuts non capturés sont acceptés en plusieurs exemplaires");

            insertPayment(c, quote, "CAPTURED", "cap-" + UUID.randomUUID());
            SQLException duplicate = rejectedInSavepoint(c,
                    () -> insertPayment(c, quote, "CAPTURED", "cap2-" + UUID.randomUUID()));
            assertEquals("23505", duplicate.getSQLState(), "un seul paiement CAPTURED par devis");
            assertEquals(1, scalarInt(c, "select count(*) from payments where quote_id = '" + quote
                    + "' and status = 'CAPTURED'"));
            c.rollback();
        }
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password)) {
            assertEquals(0, scalarInt(c, "select count(*) from quotes where id = '" + quote + "'"),
                    "rollback : le devis du scénario n'est pas persisté");
            assertEquals(0, scalarInt(c, "select count(*) from payments where quote_id = '" + quote + "'"),
                    "rollback : aucun paiement du scénario n'est persisté");
        }
    }

    @Test
    void concurrentCapturedPaymentsHaveSingleWinner() throws Exception {
        UUID quote;
        try (Connection setup = DriverManager.getConnection(jdbcUrl, user, password)) {
            quote = insertFixtureQuote(setup);
        }
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection first = DriverManager.getConnection(jdbcUrl, user, password);
                Connection monitor = DriverManager.getConnection(jdbcUrl, user, password)) {
            first.setAutoCommit(false);
            insertPayment(first, quote, "CAPTURED", "race-1-" + UUID.randomUUID());
            Future<String> second = pool.submit(() -> {
                try (Connection other = DriverManager.getConnection(jdbcUrl, user, password)) {
                    insertPayment(other, quote, "CAPTURED", "race-2-" + UUID.randomUUID());
                    return "inserted";
                } catch (SQLException ex) {
                    return "sqlstate=" + ex.getSQLState();
                }
            });
            awaitLockWait(monitor, 20);
            first.commit();
            assertEquals("sqlstate=23505", second.get(30, TimeUnit.SECONDS),
                    "deux paiements CAPTURED concurrents : une seule opération gagnante");
            assertEquals(1, scalarInt(monitor, "select count(*) from payments where quote_id = '" + quote
                    + "' and status = 'CAPTURED'"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void capturedPaymentIsPreservedWhenQuoteDeletionIsAttempted() throws SQLException {
        UUID quote;
        try (Connection setup = DriverManager.getConnection(jdbcUrl, user, password)) {
            quote = insertFixtureQuote(setup);
            insertPayment(setup, quote, "CAPTURED", "hist-" + UUID.randomUUID());
        }
        try (Connection c = tx()) {
            SQLException ex = rejectedInSavepoint(c,
                    () -> update(c, "delete from quotes where id = '" + quote + "'"));
            assertEquals("23001", ex.getSQLState(), "restrict_violation attendu (ON DELETE RESTRICT)");
            c.rollback();
        }
        try (Connection c = DriverManager.getConnection(jdbcUrl, user, password)) {
            assertEquals(1, scalarInt(c, "select count(*) from payments where quote_id = '" + quote
                    + "' and status = 'CAPTURED' and amount = 100"), "le paiement historique est intact");
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private static SQLException rejectedInSavepoint(Connection c, SqlAction action) throws SQLException {
        Savepoint savepoint = c.setSavepoint();
        try {
            action.run();
        } catch (SQLException ex) {
            c.rollback(savepoint);
            return ex;
        }
        throw new AssertionError("L'instruction SQL a réussi alors qu'elle devait être refusée");
    }

    private static void awaitLockWait(Connection monitor, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (scalarInt(monitor, "select count(*) from pg_stat_activity where datname = current_database()"
                    + " and wait_event_type = 'Lock' and pid <> pg_backend_pid()") > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("La seconde transaction n'attend pas de verrou");
    }

    /** Crée marque, catégorie, client, véhicule, réservation PENDING et devis ; identifiants générés par la base. */
    private UUID insertFixtureQuote(Connection c) throws SQLException {
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID brandId = scalarUuid(c, "insert into brands(id, name, slug) values (gen_random_uuid(), 'B" + s
                + "', 'b-" + s + "') returning id");
        UUID categoryId = scalarUuid(c, "insert into categories(id, name, slug) values (gen_random_uuid(), 'C" + s
                + "', 'c-" + s + "') returning id");
        UUID userId = scalarUuid(c, "insert into users(id, email, password_hash) values (gen_random_uuid(), 'l6-"
                + s + "@test.invalid', 'x') returning id");
        UUID customerId = scalarUuid(c, "insert into customers(id, first_name, last_name, phone)"
                + " values ('" + userId + "', 'A', 'B', '2" + s + "') returning id");
        UUID vehicleId = scalarUuid(c, "insert into vehicles(id, brand_id, category_id, model, model_year,"
                + " price_per_day, passengers, doors, transmission, fuel, description, status, price_per_week,"
                + " currency) values (gen_random_uuid(), '" + brandId + "', '" + categoryId
                + "', 'M', 2024, 100, 4, 4, 'AUTOMATIC', 'PETROL', 'd', 'AVAILABLE', 600, 'TND') returning id");
        UUID bookingId = scalarUuid(c, "insert into bookings(id, customer_id, vehicle_id, reference, starts_at,"
                + " ends_at, status, total_amount) values (gen_random_uuid(), '" + customerId + "', '" + vehicleId
                + "', 'BK" + s + "', now(), now() + interval '1 day', 'PENDING', 100) returning id");
        return scalarUuid(c, "insert into quotes(id, customer_id, vehicle_id, status, valid_until, booking_id,"
                + " reference, version, currency, starts_at, ends_at, vehicle_name, pickup_location,"
                + " dropoff_location, subtotal, discount_amount, tax_amount, total_amount) values (gen_random_uuid(), '"
                + customerId + "', '" + vehicleId + "', 'DRAFT', now() + interval '1 day', '" + bookingId
                + "', 'Q" + s + "', 1, 'TND', now(), now() + interval '1 day', 'M', 'A', 'A', 100, 0, 0, 100)"
                + " returning id");
    }

    private void insertPayment(Connection c, UUID quoteId, String status, String idempotencyKey)
            throws SQLException {
        try (var ps = c.prepareStatement("insert into payments(id, quote_id, customer_id, amount, currency,"
                + " method, status, idempotency_key) select gen_random_uuid(), q.id, q.customer_id, 100, 'TND',"
                + " 'CARD', ?, ? from quotes q where q.id = ?")) {
            ps.setString(1, status);
            ps.setString(2, idempotencyKey);
            ps.setObject(3, quoteId);
            ps.executeUpdate();
        }
    }

    private static int update(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            return st.executeUpdate(sql);
        }
    }

    private static int scalarInt(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static UUID scalarUuid(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return UUID.fromString(rs.getString(1));
        }
    }
}
