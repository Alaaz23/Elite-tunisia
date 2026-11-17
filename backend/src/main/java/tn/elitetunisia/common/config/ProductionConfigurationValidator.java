package tn.elitetunisia.common.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Refuse le démarrage en profil prod lorsqu'une variable obligatoire est absente, vide, composée d'espaces
 * ou non résolue. La vérification s'exécute avant la création du contexte, donc avant toute connexion PostgreSQL :
 * une erreur de configuration ne peut pas être confondue avec une erreur de connexion.
 * Le message ne contient que les noms des variables, jamais leurs valeurs.
 */
public class ProductionConfigurationValidator implements EnvironmentPostProcessor, Ordered {

    /** Propriété Spring résolue depuis la variable d'environnement correspondante. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("spring.datasource.url", "DATABASE_URL");
        REQUIRED.put("spring.datasource.username", "DATABASE_USERNAME");
        REQUIRED.put("spring.datasource.password", "DATABASE_PASSWORD");
        REQUIRED.put("app.security.jwt.secret", "JWT_SECRET");
        REQUIRED.put("app.cors.allowed-origins", "CORS_ALLOWED_ORIGINS");
        REQUIRED.put("app.storage.local-path", "STORAGE_LOCAL_PATH");
    }

    private static final int MIN_JWT_SECRET_BYTES = 32;

    private static final Profiles PROD = Profiles.of("prod");
    private static final Profiles DEV = Profiles.of("dev");

    @Override
    public int getOrder() {
        // Doit s'exécuter après le chargement de application*.yml, qui fournit les propriétés prod.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(PROD)) {
            return;
        }
        if (environment.acceptsProfiles(DEV)) {
            throw new IllegalStateException("Le profil dev ne doit pas être activé avec le profil prod.");
        }
        List<String> invalid = new ArrayList<>();
        REQUIRED.forEach((property, variable) -> {
            String value = resolve(environment, property);
            // Une variable absente reste sous forme de placeholder non résolu (ex. "${DATABASE_URL}").
            if (value == null || value.isBlank() || value.contains("${")) {
                invalid.add(variable);
            }
        });
        String jwtSecret = resolve(environment, "app.security.jwt.secret");
        if (jwtSecret != null && !jwtSecret.isBlank() && !jwtSecret.contains("${")
                && jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            invalid.add("JWT_SECRET (au moins " + MIN_JWT_SECRET_BYTES + " octets requis)");
        }
        if (!"true".equalsIgnoreCase(resolve(environment, "app.security.secure-cookies"))) {
            invalid.add("app.security.secure-cookies (doit valoir true)");
        }
        if (!invalid.isEmpty()) {
            throw new IllegalStateException("Configuration de production invalide, variables absentes, vides ou composées d'espaces : "
                    + String.join(", ", invalid));
        }
    }

    /** Spring lève une PlaceholderResolutionException (IllegalArgumentException) pour une variable absente. */
    private static String resolve(ConfigurableEnvironment environment, String property) {
        try {
            return environment.getProperty(property);
        } catch (IllegalArgumentException unresolved) {
            return null;
        }
    }
}
