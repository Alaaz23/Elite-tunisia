package tn.elitetunisia.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Rejoue les migrations Flyway (V1 à la dernière) sur des bases jetables contenant des données préexistantes.
 *
 * Chaque scénario part d'un modèle de base migré jusqu'à une version donnée, y insère des données,
 * exécute le contrôle de pré-migration (database/verification/preflight-before-flyway.sql), puis migre
 * jusqu'à la dernière version et compare le résultat à l'attendu documenté.
 *
 * Garde-fous : uniquement le cluster de test 127.0.0.1:15432 (même contrôle que PostgresTestHelper) ;
 * seules des bases préfixées {@value #PREFIX} sont créées puis supprimées.
 */
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
@Tag("postgres")
class MigrationWithExistingDataIT {
    private static final Pattern ALLOWED_URL =
            Pattern.compile("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):15432/et_test_db(?:\\?.*)?");
    private static final String PREFIX = "et_mig_";
    private static final int LATEST = 20;
    private static final int[] TEMPLATE_VERSIONS = {1, 3, 4, 5, 13, 15, 16, 18, 19};
    private static final List<String> REPORT = new ArrayList<>();

    private static String user;
    private static String password;

    @BeforeAll
    static void prepareTemplates() throws Exception {
        String url = System.getenv("DATABASE_URL");
        user = System.getenv("DATABASE_USERNAME");
        password = System.getenv("DATABASE_PASSWORD");
        if (url == null || !ALLOWED_URL.matcher(url).matches() || !"et_test".equals(user) || password == null) {
            throw new IllegalStateException("Ces tests n'acceptent que le cluster isole 127.0.0.1:15432 avec l'utilisateur et_test.");
        }
        try (Connection admin = connect("postgres"); Statement st = admin.createStatement();
                ResultSet rs = st.executeQuery("SELECT inet_server_port(), current_user, version()")) {
            rs.next();
            assertEquals(15432, rs.getInt(1));
            assertEquals("et_test", rs.getString(2));
            REPORT.add("Serveur : " + rs.getString(3));
            REPORT.add("");
            REPORT.add("| Scenario | Depart | Constats du pre-controle | Resultat Flyway | Derniere migration | Verifications |");
            REPORT.add("|---|---|---|---|---|---|");
        }
        for (int version : TEMPLATE_VERSIONS) {
            String template = templateName(version);
            recreate(template, null);
            flyway(template, String.valueOf(version)).migrate();
        }
    }

    @AfterAll
    static void writeReportAndDropTemplates() throws Exception {
        for (int version : TEMPLATE_VERSIONS) {
            drop(templateName(version));
        }
        Path report = Path.of("target", "migration-scenarios-report.md");
        Files.createDirectories(report.getParent());
        Files.write(report, REPORT, StandardCharsets.UTF_8);
    }

    private record Check(String sql, String expected) { }

    private record Scenario(String name, int templateVersion, List<String> seeds, Set<String> preflight,
            int expectedLastApplied, List<Check> checks) { }

    private static Check check(String sql, String expected) { return new Check(sql, expected); }

    private static List<Scenario> scenarios() {
        String roleKeyColumn = "SELECT count(*)::text FROM information_schema.columns WHERE table_name='users' AND column_name='role_key'";
        String noOverlapConstraint = "SELECT count(*)::text FROM pg_constraint WHERE conname='bookings_no_blocking_overlap'";
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("empty-database", 0, List.of(), Set.of(), LATEST, List.of(
                check("SELECT count(*)::text FROM flyway_schema_history WHERE success", String.valueOf(LATEST)),
                check(noOverlapConstraint, "1"),
                check("SELECT count(*)::text FROM information_schema.tables WHERE table_schema='public' AND table_name='transfers'", "1"))));
        list.add(new Scenario("v1-employees", 1, List.of("v1-employees"), Set.of("BLOCKER|V2_EMPLOYEES_NOT_NULL"), 1, List.of(
                check("SELECT count(*)::text FROM employees", "1"), check(roleKeyColumn, "1"))));
        list.add(new Scenario("v1-customer-null-names", 1, List.of("v1-customer-null-names"), Set.of("BLOCKER|V2_CUSTOMER_NULLS"), 1, List.of(
                check("SELECT count(*)::text FROM customers", "1"), check(roleKeyColumn, "1"))));
        list.add(new Scenario("v1-permission-conflict", 1, List.of("v1-permission-conflict"), Set.of("BLOCKER|PERMISSION_ALREADY_EXISTS"), 1, List.of(
                check("SELECT description FROM permissions WHERE permission_key='users:read'", "pre-existing"), check(roleKeyColumn, "1"))));
        list.add(new Scenario("v1-vehicles", 1, List.of("v1-vehicles"), Set.of("BLOCKER|V3_VEHICLES_NOT_NULL"), 2, List.of(
                check("SELECT count(*)::text FROM vehicles", "1"),
                check("SELECT count(*)::text FROM information_schema.columns WHERE table_name='vehicles' AND column_name='price_per_week'", "0"))));
        list.add(new Scenario("v1-vehicles-invalid-values", 1, List.of("v1-vehicles-invalid-values"), Set.of(
                "BLOCKER|V3_VEHICLES_NOT_NULL", "BLOCKER|V3_PRICE_NOT_POSITIVE", "BLOCKER|V3_YEAR_OUT_OF_RANGE", "BLOCKER|V3_ENUM_VALUES"), 2,
                List.of(check("SELECT count(*)::text FROM vehicles", "1"))));
        list.add(new Scenario("v3-bookings-valid", 3, List.of("v3-base", "v3-bookings-valid"), Set.of(), LATEST, List.of(
                check("SELECT count(*)::text FROM bookings", "2"),
                check("SELECT count(DISTINCT reference)::text FROM bookings", "2"),
                check("SELECT count(*)::text FROM bookings WHERE reference ~ '^ET-[0-9]{4}-[0-9]{6}$'", "2"),
                check("SELECT count(*)::text FROM bookings WHERE base_price = total_amount", "2"),
                check("SELECT count(*)::text FROM user_roles WHERE role_key='CUSTOMER'", "1"),
                check(noOverlapConstraint, "1"))));
        list.add(new Scenario("v3-bookings-overlap-pending", 3, List.of("v3-base", "v3-bookings-overlap-pending"), Set.of(), LATEST, List.of(
                check("SELECT count(*)::text FROM bookings WHERE status='PENDING'", "2"), check(noOverlapConstraint, "1"))));
        list.add(new Scenario("v3-bookings-overlap-confirmed", 3, List.of("v3-base", "v3-bookings-overlap-confirmed"),
                Set.of("BLOCKER|V4_OVERLAPPING_BLOCKING_BOOKINGS"), 3, List.of(
                check("SELECT count(*)::text FROM bookings", "2"),
                check("SELECT count(*)::text FROM information_schema.columns WHERE table_name='bookings' AND column_name='reference'", "0"))));
        list.add(new Scenario("v3-bookings-invalid-status", 3, List.of("v3-base", "v3-bookings-invalid-status"),
                Set.of("BLOCKER|V4_STATUS_INVALID"), 3, List.of(check("SELECT status FROM bookings", "ACTIVE"))));
        list.add(new Scenario("v4-quotes", 4, List.of("v3-base", "v4-quotes"), Set.of("BLOCKER|V5_QUOTES_PRESENT"), 4, List.of(
                check("SELECT count(*)::text FROM quotes", "1"),
                check("SELECT count(*)::text FROM pg_sequences WHERE sequencename='quote_reference_seq'", "0"))));
        list.add(new Scenario("v5-payments-invoices", 5, List.of("v3-base", "v5-payments-invoices"),
                Set.of("DATA_RISK|V6_PAYMENTS_STRANDED", "DATA_RISK|V7_INVOICES_STRANDED"), LATEST, List.of(
                check("SELECT count(*)::text FROM legacy_payments", "1"),
                check("SELECT count(*)::text FROM legacy_invoices", "1"),
                check("SELECT count(*)::text FROM payments", "0"),
                check("SELECT count(*)::text FROM invoices", "0"))));
        list.add(new Scenario("v13-permission-conflict", 13, List.of("v13-permission-conflict"),
                Set.of("BLOCKER|PERMISSION_ALREADY_EXISTS"), 13, List.of(
                check("SELECT description FROM permissions WHERE permission_key='drivers:manage'", "pre-existing"))));
        list.add(new Scenario("v15-transfers-hibernate", 15, List.of("v15-transfers-hibernate"),
                Set.of("BLOCKER|V16_TRANSFERS_PRE_EXISTING"), 15, List.of(
                check("SELECT count(*)::text FROM transfers", "1"),
                check("SELECT count(*)::text FROM pg_constraint WHERE conname LIKE 'transfers%fkey'", "0"))));
        list.add(new Scenario("v15-transfers-orphan", 15, List.of("v15-transfers-orphan"),
                Set.of("BLOCKER|V16_TRANSFERS_PRE_EXISTING", "BLOCKER|V16_TRANSFER_ORPHAN_REFERENCES"), 15, List.of(
                check("SELECT count(*)::text FROM transfers", "1"))));
        list.add(new Scenario("v18-legacy-transfers", 18, List.of("v18-legacy-transfers"),
                Set.of("DATA_RISK|V19_TRANSFER_STATUS_UNKNOWN"), LATEST, List.of(
                check("SELECT count(*)::text FROM transfers", "1"),
                check("SELECT status FROM transfers", "en cours"),
                check("SELECT count(*)::text FROM transfers WHERE booking_id IS NULL", "1"),
                check("SELECT convalidated::text FROM pg_constraint WHERE conname='transfers_status_check'", "false"),
                check("SELECT count(*)::text FROM pg_constraint WHERE conname='fk_transfers_booking'", "1"))));
        list.add(new Scenario("v19-permission-existing", 19, List.of("v19-permission-existing"), Set.of(), LATEST, List.of(
                check("SELECT description FROM permissions WHERE permission_key='services:read'", "pre-existing"),
                check("SELECT count(*)::text FROM role_permissions WHERE permission_key='services:manage'", "2"))));
        list.add(new Scenario("v16-null-columns", 16, List.of("v3-base", "v16-null-columns"), Set.of(), LATEST, List.of(
                check("SELECT metadata::text FROM invoice_lines WHERE id='eeeeeeee-0000-0000-0000-000000000002'", "{}"),
                check("SELECT refunded_amount::text FROM payments WHERE id='cccccccc-0000-0000-0000-000000000001'", "0.000"),
                check("SELECT vehicle_id::text FROM quotes WHERE id='bbbbbbbb-0000-0000-0000-000000000001'", "55555555-5555-5555-5555-555555555555"),
                check("SELECT count(*)::text FROM information_schema.columns WHERE is_nullable='YES' AND table_schema='public' AND ("
                        + "(table_name='invoice_lines' AND column_name='metadata') OR (table_name='payments' AND column_name='refunded_amount')"
                        + " OR (table_name='quotes' AND column_name='vehicle_id'))", "0"))));
        list.add(new Scenario("v16-ambiguous-refund", 16, List.of("v3-base", "v16-ambiguous-refund"),
                Set.of("BLOCKER|V17_REFUND_AMOUNT_AMBIGUOUS"), 16, List.of(
                check("SELECT count(*)::text FROM payments WHERE refunded_amount IS NULL", "1"))));
        return list;
    }

    @TestFactory
    Stream<DynamicTest> migrationScenarios() {
        return scenarios().stream().map(sc -> DynamicTest.dynamicTest(sc.name(), () -> run(sc)));
    }

    private void run(Scenario sc) throws Exception {
        String db = PREFIX + sc.name().replace('-', '_');
        recreate(db, sc.templateVersion() == 0 ? null : templateName(sc.templateVersion()));
        try {
            for (String seed : sc.seeds()) {
                try (Connection c = connect(db); Statement st = c.createStatement()) {
                    st.execute(resource(seed));
                }
            }
            Set<String> findings = preflight(db);
            Flyway flyway = flyway(db, "latest");
            String outcome = "OK";
            try {
                flyway.migrate();
            } catch (FlywayException e) {
                outcome = "ECHEC : " + firstLines(String.valueOf(e.getMessage()), 3);
            }
            int lastApplied = lastApplied(flyway);
            List<String> checkResults = new ArrayList<>();
            List<String> mismatches = new ArrayList<>();
            for (Check check : sc.checks()) {
                String actual = scalar(db, check.sql());
                boolean same = check.expected().equals(actual);
                checkResults.add(same ? "ok" : "ECART(" + actual + ")");
                if (!same) {
                    mismatches.add(check.sql() + " attendu=" + check.expected() + " obtenu=" + actual);
                }
            }
            REPORT.add("| " + sc.name() + " | V" + sc.templateVersion() + " | " + findings + " | " + outcome.replace("|", "/").replace("\n", " ")
                    + " | V" + lastApplied + " | " + checkResults.size() + " verifications, " + mismatches.size() + " ecart(s) |");
            assertEquals(List.of(), mismatches, sc.name() + " : verifications post-migration");
            assertEquals(sc.preflight(), findings, sc.name() + " : constats du pre-controle");
            assertEquals(sc.expectedLastApplied(), lastApplied, sc.name() + " : derniere migration appliquee");
            if (sc.expectedLastApplied() == LATEST) {
                assertEquals("OK", outcome, sc.name());
                flyway.migrate();
                assertEquals(LATEST, lastApplied(flyway), sc.name() + " : second passage sans effet");
            } else {
                assertTrue(outcome.startsWith("ECHEC"), sc.name() + " : un echec de migration etait attendu");
            }
        } finally {
            if (!Boolean.getBoolean("elite.keepDatabases")) {
                drop(db);
            }
        }
    }

    /** Autorités réellement exigées par le code (@PreAuthorize / SecurityConfig), relevées par recherche dans les sources. */
    private static final Set<String> AUTHORITIES_REQUIRED_BY_CODE = Set.of(
            "admin:access", "users:read", "users:manage", "vehicles:read", "vehicles:manage", "brands:read", "brands:manage",
            "categories:read", "categories:manage", "bookings:read", "bookings:create", "bookings:manage", "bookings:cancel",
            "quotes:read", "quotes:create", "quotes:manage", "quotes:send", "quotes:accept", "quotes:reject",
            "payments:create", "invoices:read", "drivers:manage", "customers:read", "services:read", "services:manage");

    @org.junit.jupiter.api.Test
    void permissionsFromMigrationsMatchWhatTheCodeRequires() throws Exception {
        String db = PREFIX + "permission_matrix";
        recreate(db, null);
        try {
            flyway(db, "latest").migrate();
            Set<String> permissions = column(db, "SELECT permission_key FROM permissions");
            assertEquals(Set.of(), difference(AUTHORITIES_REQUIRED_BY_CODE, permissions), "autorites exigees par le code mais absentes de la base");
            assertEquals(permissions.size(), Long.parseLong(scalar(db, "SELECT count(DISTINCT permission_key)::text FROM permissions")), "doublons");
            Set<String> customerOnly = Set.of("payments:create", "quotes:accept", "quotes:reject");
            Set<String> superAdmin = column(db, "SELECT permission_key FROM role_permissions WHERE role_key='SUPER_ADMIN'");
            Set<String> expectedForStaff = new TreeSet<>(AUTHORITIES_REQUIRED_BY_CODE);
            expectedForStaff.removeAll(customerOnly);
            assertEquals(Set.of(), difference(expectedForStaff, superAdmin), "SUPER_ADMIN doit detenir toutes les autorites d'administration exigees");
            assertTrue(superAdmin.stream().noneMatch(customerOnly::contains), "les actions client (paiement, acceptation/refus de devis) ne sont pas des droits staff");
            Set<String> customer = column(db, "SELECT permission_key FROM role_permissions WHERE role_key='CUSTOMER'");
            assertEquals(Set.of("bookings:read", "bookings:create", "bookings:cancel", "quotes:read", "quotes:accept", "quotes:reject",
                    "payments:create", "invoices:read", "profile:read:self", "profile:update:self"), customer);
            assertTrue(customer.stream().noneMatch(p -> p.equals("admin:access") || p.endsWith(":manage")), "un client ne doit avoir aucun droit d'administration");
            Set<String> agent = column(db, "SELECT permission_key FROM role_permissions WHERE role_key='AGENT'");
            assertTrue(!agent.contains("customers:read"), "l'annuaire clients (donnees personnelles) est reserve a SUPER_ADMIN et MANAGER");
            assertTrue(agent.contains("drivers:manage") && agent.contains("bookings:manage"));
            // Transferts et affectations : bookings:manage est réservé à SUPER_ADMIN, MANAGER et AGENT.
            for (String role : new String[] { "SUPER_ADMIN", "MANAGER", "AGENT" }) {
                assertTrue(column(db, "SELECT permission_key FROM role_permissions WHERE role_key='" + role + "'").contains("bookings:manage"), role);
            }
            for (String role : new String[] { "CONCIERGE", "ACCOUNTANT", "CUSTOMER" }) {
                assertTrue(!column(db, "SELECT permission_key FROM role_permissions WHERE role_key='" + role + "'").contains("bookings:manage"), role);
            }
            // Catalogue de services : lecture pour tout le personnel, gestion pour SUPER_ADMIN et MANAGER seulement.
            assertEquals(Set.of("SUPER_ADMIN", "MANAGER", "AGENT", "CONCIERGE", "ACCOUNTANT"),
                    column(db, "SELECT role_key FROM role_permissions WHERE permission_key='services:read'"));
            assertEquals(Set.of("SUPER_ADMIN", "MANAGER"), column(db, "SELECT role_key FROM role_permissions WHERE permission_key='services:manage'"));
            assertEquals("0", scalar(db, "SELECT count(*)::text FROM role_permissions rp LEFT JOIN permissions p USING (permission_key) WHERE p.permission_key IS NULL"));
        } finally {
            drop(db);
        }
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static Set<String> column(String db, String sql) {
        Set<String> values = new TreeSet<>();
        try (Connection c = connect(db); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return values;
    }

    private static Set<String> preflight(String db) throws SQLException, IOException {
        String sql = Files.readString(Path.of("..", "database", "verification", "preflight-before-flyway.sql"), StandardCharsets.UTF_8);
        Set<String> findings = new TreeSet<>();
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            st.execute(sql);
            for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
                String[] parts = w.getMessage().split("\\|", 4);
                if (parts.length >= 3 && parts[0].endsWith("PREFLIGHT") && !"DONE".equals(parts[2])) {
                    findings.add(parts[1] + "|" + parts[2]);
                }
            }
        }
        return findings;
    }

    private static String scalar(String db, String sql) throws SQLException {
        try (Connection c = connect(db); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int lastApplied(Flyway flyway) {
        MigrationInfo current = flyway.info().current();
        return current == null ? 0 : Integer.parseInt(current.getVersion().getVersion());
    }

    private static String firstLines(String message, int count) {
        return String.join(" / ", Stream.of(message.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).limit(count).toList());
    }

    private static String resource(String name) throws IOException {
        try (var in = MigrationWithExistingDataIT.class.getResourceAsStream("/migration-scenarios/" + name + ".sql")) {
            if (in == null) {
                fail("Jeu de donnees introuvable : " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String templateName(int version) { return PREFIX + "tpl_v" + version; }

    private static Flyway flyway(String db, String target) {
        return Flyway.configure().dataSource(url(db), user, password).locations("classpath:db/migration").target(target).load();
    }

    private static String url(String db) { return "jdbc:postgresql://127.0.0.1:15432/" + db; }

    private static Connection connect(String db) throws SQLException { return DriverManager.getConnection(url(db), user, password); }

    private static void requireOwned(String db) {
        if (!db.matches(PREFIX + "[a-z0-9_]+")) {
            throw new IllegalArgumentException("Base non geree par ce test : " + db);
        }
    }

    private static void drop(String db) throws SQLException {
        requireOwned(db);
        try (Connection admin = connect("postgres"); Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
        }
    }

    private static void recreate(String db, String template) throws SQLException {
        drop(db);
        try (Connection admin = connect("postgres"); Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + db + (template == null ? "" : " TEMPLATE " + template));
        }
    }
}
