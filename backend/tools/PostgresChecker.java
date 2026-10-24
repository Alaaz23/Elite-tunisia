package tn.elitetunisia.tools;

import java.sql.*;
import java.util.*;

public class PostgresChecker {
    public static void main(String[] args) {
        String host = System.getenv().getOrDefault("PG_HOST", "localhost");
        String port = System.getenv().getOrDefault("PG_PORT", "5432");
        String db = System.getenv().getOrDefault("PG_DB", "postgres");
        String user = System.getenv().getOrDefault("PG_USER", "postgres");
        String pass = System.getenv().getOrDefault("PG_PASS", "");
        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        System.out.println("PostgresChecker: attempting read-only JDBC connection to " + host + ":" + port + ", database=" + db + ", user=" + user + " (password not shown)");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setReadOnly(true);
            c.setAutoCommit(false);
            System.out.println("Connected. Current schema: " + c.getSchema());
            DatabaseMetaData md = c.getMetaData();
            System.out.println("DB Product: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL AS exists")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) System.out.println("flyway_schema_history present: " + rs.getBoolean(1));
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT installed_rank, version, description, success, script FROM public.flyway_schema_history ORDER BY installed_rank LIMIT 100")) {
                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("flyway_schema_history (top 100):");
                    int count = 0;
                    while (rs.next()) {
                        System.out.printf("%d | %s | %s | %s | %s\n", rs.getInt(1), rs.getString(2), rs.getString(3), rs.getObject(4), rs.getString(5));
                        count++;
                    }
                    if (count==0) System.out.println("  (no rows)");
                }
            } catch (SQLException e) {
                System.out.println("Could not read flyway_schema_history: " + e.getMessage());
            }

            String[] tables = {"vehicles","drivers","payments","invoices","booking_driver_assignments"};
            for (String t: tables) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name=? ORDER BY ordinal_position")) {
                    ps.setString(1, t);
                    try (ResultSet rs = ps.executeQuery()) {
                        System.out.println("Columns for table: " + t);
                        boolean any = false;
                        while (rs.next()) {
                            any = true;
                            System.out.printf("  %s | %s | nullable=%s\n", rs.getString(1), rs.getString(2), rs.getString(3));
                        }
                        if (!any) System.out.println("  (table not found or no columns)");
                    }
                } catch (SQLException e) {
                    System.out.println("  could not inspect table " + t + ": " + e.getMessage());
                }
            }

            try (PreparedStatement ps = c.prepareStatement("SELECT datname FROM pg_database WHERE datistemplate = false LIMIT 50")) {
                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("Databases visible (non-template):");
                    while (rs.next()) System.out.println("  " + rs.getString(1));
                }
            } catch (SQLException e) {
                System.out.println("Could not list databases: " + e.getMessage());
            }

        } catch (SQLException e) {
            System.out.println("JDBC connection failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
