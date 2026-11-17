package tn.elitetunisia.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {

    private static final String[] VARIABLES = { "DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD", "JWT_SECRET",
            "CORS_ALLOWED_ORIGINS", "STORAGE_LOCAL_PATH" };

    private final ProductionConfigurationValidator validator = new ProductionConfigurationValidator();

    /** Reproduit application-prod.yml : chaque propriété référence une variable, sans valeur par défaut. */
    private static MockEnvironment prodEnvironment(String variable, String value) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", "${DATABASE_URL}");
        env.setProperty("spring.datasource.username", "${DATABASE_USERNAME}");
        env.setProperty("spring.datasource.password", "${DATABASE_PASSWORD}");
        env.setProperty("app.security.jwt.secret", "${JWT_SECRET}");
        env.setProperty("app.cors.allowed-origins", "${CORS_ALLOWED_ORIGINS}");
        env.setProperty("app.storage.local-path", "${STORAGE_LOCAL_PATH}");
        env.setProperty("app.security.secure-cookies", "true");
        for (String name : VARIABLES) {
            if (name.equals(variable)) {
                if (value != null) {
                    env.setProperty(name, value);
                }
            } else {
                env.setProperty(name, "JWT_SECRET".equals(name) ? "configured-jwt-secret-value-for-test-32b" : "configured-value-for-test");
            }
        }
        return env;
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(VARIABLES).flatMap(name -> Stream.of(
                Arguments.of(name, null),
                Arguments.of(name, ""),
                Arguments.of(name, "   ")));
    }

    @Test
    void acceptsCompleteProductionConfiguration() {
        assertDoesNotThrow(() -> validator.postProcessEnvironment(prodEnvironment(null, null), null));
    }

    @ParameterizedTest(name = "{0} = [{1}]")
    @MethodSource("invalidValues")
    void rejectsAbsentEmptyOrBlankVariable(String variable, String value) {
        var error = assertThrows(IllegalStateException.class,
                () -> validator.postProcessEnvironment(prodEnvironment(variable, value), null));
        assertTrue(error.getMessage().contains(variable), error.getMessage());
    }

    @Test
    void rejectsJwtSecretShorterThan32Bytes() {
        MockEnvironment env = prodEnvironment(null, null);
        env.setProperty("JWT_SECRET", "x".repeat(31));
        var error = assertThrows(IllegalStateException.class, () -> validator.postProcessEnvironment(env, null));
        assertTrue(error.getMessage().contains("JWT_SECRET"), error.getMessage());
        assertFalse(error.getMessage().contains("xxxx"), "le secret ne doit pas apparaître dans le message");
    }

    @Test
    void rejectsSecureCookiesDisabledInProduction() {
        MockEnvironment env = prodEnvironment(null, null);
        env.setProperty("app.security.secure-cookies", "false");
        assertThrows(IllegalStateException.class, () -> validator.postProcessEnvironment(env, null));
    }

    @Test
    void rejectsDevProfileCombinedWithProd() {
        MockEnvironment env = prodEnvironment(null, null);
        env.setActiveProfiles("prod", "dev");
        assertThrows(IllegalStateException.class, () -> validator.postProcessEnvironment(env, null));
    }

    @Test
    void doesNotValidateNonProductionProfiles() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("spring.datasource.url", "${DATABASE_URL}");
        assertDoesNotThrow(() -> validator.postProcessEnvironment(env, null));
    }
}
