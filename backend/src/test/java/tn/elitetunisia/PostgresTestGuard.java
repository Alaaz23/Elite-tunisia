package tn.elitetunisia;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garde-fou de la configuration PostgreSQL des tests. Valide les variables ELITE_POSTGRES_URL,
 * ELITE_POSTGRES_USERNAME et ELITE_POSTGRES_PASSWORD sans ouvrir de connexion.
 *
 * <p>Seul le cluster isolé 127.0.0.1:15432 (ou localhost:15432) est accepté, avec une base dont le nom
 * se termine explicitement par {@code _test}. Le port 5432 et les bases système ou de développement sont refusés.
 */
public final class PostgresTestGuard {
    static final String URL_VAR = "ELITE_POSTGRES_URL";
    static final String USER_VAR = "ELITE_POSTGRES_USERNAME";
    static final String PASSWORD_VAR = "ELITE_POSTGRES_PASSWORD";

    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://(127\\.0\\.0\\.1|localhost):15432/([a-z][a-z0-9]*(?:_[a-z0-9]+)*_test)");
    private static final java.util.Set<String> FORBIDDEN_USERS = java.util.Set.of("postgres");

    public record Config(String url, String database, String username, String password) {}

    private PostgresTestGuard() {}

    public static Config validate(String url, String username, String password) {
        requireValue(URL_VAR, url);
        requireValue(USER_VAR, username);
        requireValue(PASSWORD_VAR, password);
        Matcher matcher = URL.matcher(url);
        if (!matcher.matches()) {
            throw refuse(URL_VAR, "doit être jdbc:postgresql://127.0.0.1:15432/<base>_test "
                    + "(port 15432 explicite, hôte 127.0.0.1 ou localhost, base dédiée aux tests). Valeur reçue : " + url);
        }
        if (FORBIDDEN_USERS.contains(username)) {
            throw refuse(USER_VAR, "le rôle superutilisateur '" + username + "' est interdit pour les tests.");
        }
        return new Config(url, matcher.group(2), username, password);
    }

    private static void requireValue(String name, String value) {
        if (value == null || value.isBlank()) {
            throw refuse(name, "variable absente ou vide ; configuration PostgreSQL incomplète.");
        }
        if (value.contains("${")) {
            throw refuse(name, "valeur non résolue (placeholder Maven/Spring non remplacé) : " + name + " doit être défini.");
        }
    }

    private static IllegalStateException refuse(String name, String reason) {
        return new IllegalStateException("Garde-fou PostgreSQL des tests refusé (" + name + ") : " + reason);
    }
}
