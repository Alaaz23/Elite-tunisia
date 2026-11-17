package tn.elitetunisia;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuse tout contexte de test dont la source de données est PostgreSQL et dont Hibernate
 * génère ou supprime le schéma (create, create-drop, update).
 *
 * <p>Protège elite_a4_test contre un DDL hérité des tests H2 qui partagent la JVM.
 */
public class PostgresDdlSafetyPostProcessor implements EnvironmentPostProcessor {

    static final String URL_KEY = "spring.datasource.url";
    static final String DDL_KEY = "spring.jpa.hibernate.ddl-auto";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        checkSafe(environment.getProperty(URL_KEY), environment.getProperty(DDL_KEY));
    }

    static void checkSafe(String url, String ddlAuto) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:")) {
            return;
        }
        String ddl = ddlAuto == null ? "" : ddlAuto.trim().toLowerCase(Locale.ROOT);
        if (ddl.equals("create") || ddl.equals("create-drop") || ddl.equals("update")) {
            throw new IllegalStateException("Refus : ddl-auto=" + ddl + " sur une URL PostgreSQL ("
                    + url + "). Un contexte H2 a hérité de la configuration PostgreSQL de la JVM. "
                    + "Exécutez les tests H2 dans une JVM séparée.");
        }
    }
}
