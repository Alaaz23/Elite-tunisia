package tn.elitetunisia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Identités de test uniques. Chaque email généré est enregistré dans un registre exact ; le nettoyage
 * ne désactive que ces adresses précises, jamais un motif générique. Les emails portent le préfixe
 * {@code it-}, le scénario, l'identifiant de la JVM et un suffixe aléatoire.
 */
public final class TestIdentities {

    public static final String DOMAIN = "@example.test";
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final Set<String> CREATED = ConcurrentHashMap.newKeySet();

    private TestIdentities() {
    }

    /** Email unique à chaque appel. */
    public static String email(String scenario) {
        String address = "it-" + scenario + "-" + RUN_ID + "-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
        CREATED.add(address);
        return address;
    }

    /** Email stable pour toute la durée de la JVM (même scénario = même adresse). */
    public static String runEmail(String scenario) {
        String address = "it-" + scenario + "-" + RUN_ID + DOMAIN;
        CREATED.add(address);
        return address;
    }

    /**
     * Désactive (sans supprimer) uniquement les comptes enregistrés par ce helper, puis vide le registre.
     * Les comptes métier et ceux des autres exécutions ne sont jamais concernés.
     */
    public static int disableCreated(JdbcTemplate jdbc) {
        if (CREATED.isEmpty()) {
            return 0;
        }
        List<String> addresses = new ArrayList<>(CREATED);
        String placeholders = String.join(",", Collections.nCopies(addresses.size(), "?"));
        int updated = jdbc.update(
            "update users set enabled = false, updated_at = now() where enabled = true and email in (" + placeholders + ")",
            addresses.toArray());
        CREATED.removeAll(addresses);
        return updated;
    }
}
