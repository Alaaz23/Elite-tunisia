package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostgresDdlSafetyPostProcessorTest {

    private static final String PG = "jdbc:postgresql://127.0.0.1:15432/elite_a4_test";
    private static final String H2 = "jdbc:h2:mem:elite_test;MODE=PostgreSQL";

    @Test
    void refusesDestructiveDdlOnPostgres() {
        assertThrows(IllegalStateException.class, () -> PostgresDdlSafetyPostProcessor.checkSafe(PG, "create-drop"));
        assertThrows(IllegalStateException.class, () -> PostgresDdlSafetyPostProcessor.checkSafe(PG, "create"));
        assertThrows(IllegalStateException.class, () -> PostgresDdlSafetyPostProcessor.checkSafe(PG, "update"));
    }

    @Test
    void allowsValidateAndNoneOnPostgres() {
        assertDoesNotThrow(() -> PostgresDdlSafetyPostProcessor.checkSafe(PG, "validate"));
        assertDoesNotThrow(() -> PostgresDdlSafetyPostProcessor.checkSafe(PG, "none"));
    }

    @Test
    void allowsCreateDropOnH2() {
        assertDoesNotThrow(() -> PostgresDdlSafetyPostProcessor.checkSafe(H2, "create-drop"));
    }
}
