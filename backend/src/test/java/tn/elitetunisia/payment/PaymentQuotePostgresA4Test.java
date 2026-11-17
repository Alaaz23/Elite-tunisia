package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests A4 sur PostgreSQL isolé : migrations V1 à V22, contrainte RESTRICT sur fk_payments_quote,
 * index partiel CAPTURED unique et préservation des paiements.
 *
 * <p>Activation : ELITE_A4_PG_TEST=true, ELITE_A4_PG_URL (jdbc:postgresql://127.0.0.1:15432/postgres),
 * ELITE_A4_PG_USER, ELITE_A4_PG_PASSWORD. Crée une base temporaire dédiée et la supprime à la fin.
 * Refuse tout hôte/port autre que 127.0.0.1|localhost:15432 et toute base réservée.
 *
 * <p>Chaque scénario s'exécute dans une transaction annulée (rollback) : aucune donnée ne persiste.
 */
class PaymentQuotePostgresA4Test {

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
                "Test A4 désactivé : définir " + ENABLED_FLAG + "=true");
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
                .target("22")
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

    /** Garde-fou : hôte local, port 15432, base au préfixe de test, jamais une base réservée. */
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
    void migrationsV1ToV22AreAppliedAndForeignKeyIsRestrict() throws SQLException {
        try (Connection c = tx()) {
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery(
                            "select confdeltype::text from pg_constraint where conname='fk_payments_quote'")) {
                assertTrue(rs.next(), "fk_payments_quote absente");
                assertEquals("r", rs.getString(1), "ON DELETE RESTRICT attendu");
            }
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("select count(*) from flyway_schema_history where success")) {
                rs.next();
                assertEquals(22, rs.getInt(1), "22 migrations réussies attendues");
            }
            c.rollback();
        }
    }

    /**
     * V22 laisse la contrainte NOT VALID (décision A4). La validation est exécutée ici après contrôle
     * d'absence d'orphelins ; VALIDATE CONSTRAINT est transactionnel, donc annulé par rollback.
     */
    @Test
    void foreignKeyCanBeValidatedAfterOrphanCheckAndRollsBack() throws SQLException {
        try (Connection c = tx()) {
            assertEquals(false, isValidated(c), "NOT VALID attendu après V22");
            assertEquals(0L, scalarLong(c, "select count(*) from payments p where p.quote_id is not null"
                    + " and not exists (select 1 from quotes q where q.id = p.quote_id)"),
                    "Orphelins de paiements détectés : validation interdite");
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE payments VALIDATE CONSTRAINT fk_payments_quote");
            }
            assertTrue(isValidated(c), "fk_payments_quote doit être validée");
            c.rollback();
        }
    }

    @Test
    void deletingQuoteWithPaymentIsRefusedAndPaymentsArePreserved() throws SQLException {
        try (Connection c = tx()) {
            UUID quoteId = insertFixtureQuote(c);
            insertPayment(c, quoteId, "PENDING", null);
            insertPayment(c, quoteId, "FAILED", null);
            assertEquals(2, countPayments(c, quoteId));

            SQLException ex = assertThrows(SQLException.class, () -> deleteQuote(c, quoteId));
            assertEquals("23001", ex.getSQLState(), "restrict_violation attendu (ON DELETE RESTRICT)");
            c.rollback();
        }
    }

    @Test
    void secondCapturedPaymentForSameQuoteIsRefused() throws SQLException {
        try (Connection c = tx()) {
            UUID quoteId = insertFixtureQuote(c);
            insertPayment(c, quoteId, "CAPTURED", "k-" + UUID.randomUUID());
            SQLException ex = assertThrows(SQLException.class,
                    () -> insertPayment(c, quoteId, "CAPTURED", "k-" + UUID.randomUUID()));
            assertEquals("23505", ex.getSQLState(), "unique_violation attendu");
            c.rollback();
        }
    }

    @Test
    void otherPaymentStatusesAreAcceptedAlongsideCaptured() throws SQLException {
        try (Connection c = tx()) {
            UUID quoteId = insertFixtureQuote(c);
            insertPayment(c, quoteId, "CAPTURED", null);
            insertPayment(c, quoteId, "PENDING", null);
            insertPayment(c, quoteId, "FAILED", null);
            insertPayment(c, quoteId, "PENDING", null);
            assertEquals(4, countPayments(c, quoteId));
            c.rollback();
        }
    }

    @Test
    void quoteWithoutPaymentCanBeDeleted() throws SQLException {
        try (Connection c = tx()) {
            UUID quoteId = insertFixtureQuote(c);
            assertEquals(1, deleteQuote(c, quoteId));
            c.rollback();
        }
    }

    private boolean isValidated(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "select convalidated from pg_constraint where conname='fk_payments_quote'")) {
            assertTrue(rs.next(), "fk_payments_quote absente");
            return rs.getBoolean(1);
        }
    }

    /** Crée marque, catégorie, client, véhicule, réservation et devis ; les identifiants sont générés par la base. */
    private UUID insertFixtureQuote(Connection c) throws SQLException {
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID brandId = scalarUuid(c, "insert into brands(id, name, slug) values (gen_random_uuid(), 'B" + s
                + "', 'b-" + s + "') returning id");
        UUID categoryId = scalarUuid(c, "insert into categories(id, name, slug) values (gen_random_uuid(), 'C" + s
                + "', 'c-" + s + "') returning id");
        UUID userId = scalarUuid(c, "insert into users(id, email, password_hash) values (gen_random_uuid(), 'a4-"
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

    private int deleteQuote(Connection c, UUID quoteId) throws SQLException {
        try (var ps = c.prepareStatement("delete from quotes where id = ?")) {
            ps.setObject(1, quoteId);
            return ps.executeUpdate();
        }
    }

    private int countPayments(Connection c, UUID quoteId) throws SQLException {
        try (var ps = c.prepareStatement("select count(*) from payments where quote_id = ?")) {
            ps.setObject(1, quoteId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private UUID scalarUuid(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        }
    }

    private long scalarLong(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
