package tn.elitetunisia.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Tests lot 2, lot 3, lot 4 et lot 5 sur PostgreSQL isolé : chaîne Flyway V1 à V26, partenaires, rattachement véhicule
 * et historique append-only des affectations, rôle PARTNER, garanties (chevauchement, durée, immuabilité, concurrence).
 *
 * <p>Activation : ELITE_A4_PG_TEST=true, ELITE_A4_PG_URL (jdbc:postgresql://127.0.0.1:15432/postgres),
 * ELITE_A4_PG_USER, ELITE_A4_PG_PASSWORD. Crée une base temporaire {@code et_a4_it_*} et la supprime.
 * Chaque scénario est annulé par rollback.
 */
class PartnerMigrationPostgresTest {

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
                "Test lot 2 désactivé : définir " + ENABLED_FLAG + "=true");
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
    void allMigrationsV1ToV26AreSuccessful() throws SQLException {
        try (Connection c = tx()) {
            assertEquals(26, scalarInt(c, "select count(*) from flyway_schema_history where success"),
                    "26 migrations réussies attendues");
            c.rollback();
        }
    }

    @Test
    void vehicleStatusAcceptsArchivedAndRejectsUnknownValues() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            execute(c, "update vehicles set status = 'ARCHIVED' where id = '" + vehicle + "'");
            SQLException ex = assertThrows(SQLException.class,
                    () -> execute(c, "update vehicles set status = 'DELETED' where id = '" + vehicle + "'"));
            assertEquals("23514", ex.getSQLState(), "check_violation attendu");
            c.rollback();
        }
    }

    @Test
    void partnerTypeIsRestrictedToIndividualOrCompany() throws SQLException {
        try (Connection c = tx()) {
            insertPartner(c, "INDIVIDUAL");
            insertPartner(c, "COMPANY");
            SQLException ex = assertThrows(SQLException.class, () -> insertPartner(c, "OTHER"));
            assertEquals("23514", ex.getSQLState(), "check_violation attendu");
            c.rollback();
        }
    }

    @Test
    void onlyOneCurrentAssignmentPerVehicle() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID p1 = insertPartner(c, "COMPANY");
            UUID p2 = insertPartner(c, "INDIVIDUAL");
            UUID admin = insertUser(c);
            insertAssignment(c, vehicle, p1, admin);
            SQLException ex = assertThrows(SQLException.class, () -> insertAssignment(c, vehicle, p2, admin));
            assertEquals("23505", ex.getSQLState(), "unique_violation attendu");
            c.rollback();
        }
    }

    @Test
    void assignmentHistoryCannotBeDeleted() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "INDIVIDUAL");
            UUID admin = insertUser(c);
            UUID assignment = insertAssignment(c, vehicle, partner, admin);
            SQLException ex = assertThrows(SQLException.class, () -> execute(c,
                    "delete from vehicle_partner_assignments where id = '" + assignment + "'"));
            assertEquals("23001", ex.getSQLState(), "restrict_violation attendu");
            c.rollback();
        }
    }

    @Test
    void assignmentCannotBeRewrittenButCanBeClosed() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID other = insertPartner(c, "INDIVIDUAL");
            UUID admin = insertUser(c);
            UUID assignment = insertAssignment(c, vehicle, partner, admin);
            SQLException rewrite = assertThrows(SQLException.class, () -> execute(c,
                    "update vehicle_partner_assignments set partner_id = '" + other + "' where id = '"
                            + assignment + "'"));
            assertEquals("23001", rewrite.getSQLState());
            c.rollback();
        }
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            UUID assignment = insertAssignment(c, vehicle, partner, admin);
            execute(c, "update vehicle_partner_assignments set ended_at = now() where id = '" + assignment + "'");
            assertEquals(1, scalarInt(c, "select count(*) from vehicle_partner_assignments where id = '"
                    + assignment + "' and ended_at is not null"));
            c.rollback();
        }
    }

    @Test
    void partnerReferencedByVehicleCannotBeDeleted() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "INDIVIDUAL");
            execute(c, "update vehicles set partner_id = '" + partner + "' where id = '" + vehicle + "'");
            SQLException ex = assertThrows(SQLException.class, () -> execute(c,
                    "delete from partners where id = '" + partner + "'"));
            assertEquals("23001", ex.getSQLState(), "partenaire référencé : suppression refusée");
            c.rollback();
        }
    }

    @Test
    void partnerRoleAndPermissionsAreSeeded() throws SQLException {
        try (Connection c = tx()) {
            assertEquals(1, scalarInt(c, "select count(*) from roles where role_key = 'PARTNER'"));
            assertEquals(1, scalarInt(c, "select count(*) from role_permissions where role_key = 'PARTNER'"
                    + " and permission_key = 'partner-portal:read'"));
            assertEquals(0, scalarInt(c, "select count(*) from role_permissions where role_key = 'PARTNER'"
                    + " and permission_key = 'partners:manage'"), "PARTNER ne doit pas gérer les partenaires");
            c.rollback();
        }
    }

    @Test
    void guaranteeOverlappingActiveGuaranteeIsRejectedWithExclusionViolation() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'", "now() + interval '3 days'");
            SQLException ex = assertThrows(SQLException.class, () -> insertGuarantee(c, vehicle, partner, admin,
                    "now() + interval '2 days'", "now() + interval '4 days'"));
            assertEquals("23P01", ex.getSQLState(), "chevauchement refusé par le trigger");
            c.rollback();
        }
    }

    @Test
    void adjacentGuaranteesAreAccepted() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'", "now() + interval '3 days'");
            insertGuarantee(c, vehicle, partner, admin, "now() + interval '3 days'", "now() + interval '4 days'");
            assertEquals(2, scalarInt(c, "select count(*) from vehicle_guarantees where vehicle_id = '" + vehicle + "'"));
            c.rollback();
        }
    }

    @Test
    void guaranteeDurationIsBoundedTo90Days() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'", "now() + interval '91 days'");
            c.rollback();
        }
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            SQLException ex = assertThrows(SQLException.class, () -> insertGuarantee(c, vehicle, partner, admin,
                    "now() + interval '1 day'", "now() + interval '92 days'"));
            assertEquals("23514", ex.getSQLState(), "durée > 90 jours refusée");
            c.rollback();
        }
    }

    @Test
    void guaranteeEndMustBeAfterStart() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            SQLException ex = assertThrows(SQLException.class, () -> insertGuarantee(c, vehicle, partner, admin,
                    "now() + interval '1 day'", "now() + interval '1 day'"));
            assertEquals("23514", ex.getSQLState());
            c.rollback();
        }
    }

    @Test
    void guaranteeCanOnlyBeCreatedAsActive() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            SQLException ex = assertThrows(SQLException.class, () -> execute(c,
                    "insert into vehicle_guarantees(id, vehicle_id, partner_id, starts_at, ends_at, status, reason,"
                            + " created_by_user_id, withdrawn_at, withdrawn_by_user_id) values (gen_random_uuid(), '"
                            + vehicle + "', '" + partner + "', now() + interval '1 day', now() + interval '2 days',"
                            + " 'WITHDRAWN', 'test', '" + admin + "', now(), '" + admin + "')"));
            assertEquals("23001", ex.getSQLState(), "création directe en WITHDRAWN refusée");
            c.rollback();
        }
    }

    @Test
    void guaranteeHistoryCannotBeDeletedOrRewritten() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID other = insertPartner(c, "INDIVIDUAL");
            UUID admin = insertUser(c);
            UUID guarantee = insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'",
                    "now() + interval '3 days'");
            SQLException del = rejectedInSavepoint(c, () -> execute(c,
                    "delete from vehicle_guarantees where id = '" + guarantee + "'"));
            assertEquals("23001", del.getSQLState(), "suppression interdite");
            SQLException rewrite = rejectedInSavepoint(c, () -> execute(c,
                    "update vehicle_guarantees set partner_id = '" + other + "' where id = '" + guarantee + "'"));
            assertEquals("23001", rewrite.getSQLState(), "partenaire engagé immuable");
            c.rollback();
        }
    }

    @Test
    void guaranteeKeepsEngagedPartnerWhenVehicleChangesPartner() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID first = insertPartner(c, "COMPANY");
            UUID second = insertPartner(c, "INDIVIDUAL");
            UUID admin = insertUser(c);
            UUID assignment = insertAssignment(c, vehicle, first, admin);
            UUID guarantee = insertGuarantee(c, vehicle, first, admin, "now() + interval '1 day'",
                    "now() + interval '3 days'");
            execute(c, "update vehicle_partner_assignments set ended_at = now() where id = '" + assignment + "'");
            execute(c, "update vehicles set partner_id = '" + second + "' where id = '" + vehicle + "'");
            insertAssignment(c, vehicle, second, admin);
            assertEquals(1, scalarInt(c, "select count(*) from vehicle_guarantees where id = '" + guarantee
                    + "' and partner_id = '" + first + "'"), "la garantie conserve le partenaire engagé");
            assertEquals(2, scalarInt(c, "select count(*) from vehicle_partner_assignments where vehicle_id = '"
                    + vehicle + "'"), "les anciennes affectations sont conservées");
            c.rollback();
        }
    }

    @Test
    void withdrawalIsAllowedOnceAndFreesThePeriod() throws SQLException {
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            UUID guarantee = insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'",
                    "now() + interval '3 days'");
            SQLException incomplete = assertThrows(SQLException.class, () -> execute(c,
                    "update vehicle_guarantees set status = 'WITHDRAWN' where id = '" + guarantee + "'"));
            assertEquals("23514", incomplete.getSQLState(), "retrait sans auteur ni date refusé");
            c.rollback();
        }
        try (Connection c = tx()) {
            UUID vehicle = insertVehicle(c);
            UUID partner = insertPartner(c, "COMPANY");
            UUID admin = insertUser(c);
            UUID guarantee = insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'",
                    "now() + interval '3 days'");
            execute(c, "update vehicle_guarantees set status = 'WITHDRAWN', withdrawn_by_user_id = '" + admin
                    + "', withdrawn_at = now() where id = '" + guarantee + "'");
            SQLException reopen = rejectedInSavepoint(c, () -> execute(c,
                    "update vehicle_guarantees set status = 'ACTIVE', withdrawn_by_user_id = null, withdrawn_at = null"
                            + " where id = '" + guarantee + "'"));
            assertEquals("23001", reopen.getSQLState(), "une garantie retirée ne peut pas être réactivée");
            insertGuarantee(c, vehicle, partner, admin, "now() + interval '1 day'", "now() + interval '3 days'");
            c.rollback();
        }
    }

    @Test
    void concurrentOverlappingGuaranteeWaitsOnVehicleLockThenIsRejected() throws Exception {
        UUID vehicle;
        UUID partner;
        UUID admin;
        try (Connection setup = DriverManager.getConnection(jdbcUrl, user, password)) {
            vehicle = insertVehicle(setup);
            partner = insertPartner(setup, "COMPANY");
            admin = insertUser(setup);
        }
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection first = DriverManager.getConnection(jdbcUrl, user, password);
                Connection monitor = DriverManager.getConnection(jdbcUrl, user, password)) {
            first.setAutoCommit(false);
            insertGuarantee(first, vehicle, partner, admin, "now() + interval '10 days'", "now() + interval '12 days'");
            Future<SQLException> second = pool.submit(() -> {
                try (Connection other = DriverManager.getConnection(jdbcUrl, user, password)) {
                    insertGuarantee(other, vehicle, partner, admin, "now() + interval '11 days'",
                            "now() + interval '13 days'");
                    return null;
                } catch (SQLException ex) {
                    return ex;
                }
            });
            awaitLockWait(monitor, 20);
            first.commit();
            SQLException rejected = second.get(30, TimeUnit.SECONDS);
            assertNotNull(rejected, "la seconde garantie chevauchante doit être refusée après commit de la première");
            assertEquals("23P01", rejected.getSQLState());
        } finally {
            pool.shutdownNow();
        }
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
        throw new AssertionError("La seconde transaction n'attend pas le verrou du véhicule");
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private static SQLException rejectedInSavepoint(Connection c, SqlAction action) throws SQLException {
        java.sql.Savepoint savepoint = c.setSavepoint();
        try {
            action.run();
        } catch (SQLException ex) {
            c.rollback(savepoint);
            return ex;
        }
        throw new AssertionError("L'instruction SQL a réussi alors qu'elle devait être refusée");
    }

    private UUID insertGuarantee(Connection c, UUID vehicle, UUID partner, UUID admin, String startsSql,
            String endsSql) throws SQLException {
        return scalarUuid(c, "insert into vehicle_guarantees(id, vehicle_id, partner_id, starts_at, ends_at, reason,"
                + " created_by_user_id) values (gen_random_uuid(), '" + vehicle + "', '" + partner + "', " + startsSql
                + ", " + endsSql + ", 'test', '" + admin + "') returning id");
    }

    private UUID insertUser(Connection c) throws SQLException {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return scalarUuid(c, "insert into users(id, email, password_hash) values (gen_random_uuid(), 'p-" + s
                + "@test.invalid', 'x') returning id");
    }

    private UUID insertPartner(Connection c, String type) throws SQLException {
        String s = UUID.randomUUID().toString().substring(0, 8);
        return scalarUuid(c, "insert into partners(id, name, partner_type, active) values (gen_random_uuid(), 'P"
                + s + "', '" + type + "', true) returning id");
    }

    private UUID insertVehicle(Connection c) throws SQLException {
        String s = UUID.randomUUID().toString().substring(0, 8);
        UUID brand = scalarUuid(c, "insert into brands(id, name, slug) values (gen_random_uuid(), 'B" + s
                + "', 'b-" + s + "') returning id");
        UUID category = scalarUuid(c, "insert into categories(id, name, slug) values (gen_random_uuid(), 'C" + s
                + "', 'c-" + s + "') returning id");
        return scalarUuid(c, "insert into vehicles(id, brand_id, category_id, model, model_year, price_per_day,"
                + " passengers, doors, transmission, fuel, description, status, price_per_week, currency)"
                + " values (gen_random_uuid(), '" + brand + "', '" + category
                + "', 'M', 2024, 100, 4, 4, 'AUTOMATIC', 'PETROL', 'd', 'AVAILABLE', 600, 'TND') returning id");
    }

    private UUID insertAssignment(Connection c, UUID vehicle, UUID partner, UUID admin) throws SQLException {
        return scalarUuid(c, "insert into vehicle_partner_assignments(id, vehicle_id, partner_id, reason,"
                + " assigned_by_user_id) values (gen_random_uuid(), '" + vehicle + "', '" + partner
                + "', 'test', '" + admin + "') returning id");
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
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
