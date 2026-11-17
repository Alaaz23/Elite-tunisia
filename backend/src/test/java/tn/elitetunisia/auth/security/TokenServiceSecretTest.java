package tn.elitetunisia.auth.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TokenServiceSecretTest {

    @Test
    void rejectsSecretComposedOnlyOfSpacesEvenWhenLongEnough() {
        String spaces = " ".repeat(40);
        assertThrows(IllegalStateException.class, () -> new TokenService(spaces, 15));
    }

    @Test
    void rejectsAbsentOrEmptySecret() {
        assertThrows(IllegalStateException.class, () -> new TokenService(null, 15));
        assertThrows(IllegalStateException.class, () -> new TokenService("", 15));
    }

    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThrows(IllegalStateException.class, () -> new TokenService("short-secret", 15));
    }

    @Test
    void acceptsSecretOfAtLeast32Bytes() {
        assertDoesNotThrow(() -> new TokenService("0123456789abcdef0123456789abcdef", 15));
    }
}
