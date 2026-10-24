package tn.elitetunisia.tools;

import java.sql.*;
import java.util.*;

public class PostgresCheckerV2 {
    public static void main(String[] args) {
        String host = System.getenv().getOrDefault("PG_HOST", "localhost");
        String port = System.getenv().getOrDefault("PG_PORT", "5432");
        String db = System.getenv().getOrDefault("PG_DB", "postgres");
        String user = System.getenv().getOrDefault("PG_USER", "postgres");
        String pass = System.getenv().getOrDefault("PG_PASS", "");
        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        System.out.println("PostgresCheckerV2: connecting read-only to " + url + " as " + user + " (password hidden)");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setReadOnly(true);
            c.setAutoCommit(true);
            DatabaseMetaData md = c.getMetaData();
            System.out.println("DB: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());

            // list accessible schemas
            try (ResultSet rs = md.getSchemas()) {
                System.out.println("Schemas:");
                while (rs.next()) System.out.println("  " + rs.getString("TABLE_SCHEM"));
            }

            // check flyway table existence across schemas
            System.out.println("Checking flyway_schema_history presence by schema:");
            List<String> schemas = new ArrayList<>();
            try (ResultSet rs = md.getSchemas()) { while (rs.next()) schemas.add(rs.getString("TABLE_SCHEM")); }
            for (String s : schemas) {
                try (PreparedStatement ps = c.prepareStatement("SELECT to_regclass(?) IS NOT NULL AS exists")) {
                    ps.setString(1, s + ".flyway_schema_history");
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) System.out.println("  " + s + ": " + rs.getBoolean(1)); }
                }
            }

            // list tables in public and other schemas
            for (String s : List.of("public")) {
                System.out.println("Tables in schema: " + s);
                try (ResultSet rs = md.getTables(null, s, "%", new String[] {"TABLE"})) {
                    boolean any = false;
                    while (rs.next()) { any = true; System.out.println("  " + rs.getString("TABLE_NAME")); }
                    if (!any) System.out.println("  (no tables found in " + s + ")");
                }
            }

            // for a short list of business tables, list columns if present
            String[] business = {"vehicles","drivers","payments","invoices","booking_driver_assignments","bookings","quotes"};
            for (String t : business) {
                try (ResultSet rs = md.getColumns(null, "public", t, "%")) {
                    boolean any = false;
                    System.out.println("Columns for public." + t + ":");
                    while (rs.next()) {
                        any = true;
                        System.out.println("  " + rs.getString("COLUMN_NAME") + " | " + rs.getString("TYPE_NAME") + " | nullable=" + rs.getString("IS_NULLABLE"));
                    }
                    if (!any) System.out.println("  (table not present)");
                } catch (SQLException e) {
                    System.out.println("  could not inspect public." + t + ": " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.out.println("Connection failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
