package tn.elitetunisia.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class TokenServiceValidationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final TokenService tokens = new TokenService(SECRET, 15);
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void acceptsTokenIssuedByTheService() {
        assertEquals("client@example.test", tokens.parse(tokens.createAccessToken("client@example.test")).getSubject());
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        SecretKey forgedKey = Keys.hmacShaKeyFor("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().subject("admin@example.test")
                .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(forgedKey).compact();
        assertThrows(JwtException.class, () -> tokens.parse(forged));
    }

    @Test
    void rejectsTamperedPayloadWithOriginalSignature() {
        String valid = tokens.createAccessToken("client@example.test");
        String[] parts = valid.split("\\.");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin@example.test\",\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> tokens.parse(parts[0] + "." + payload + "." + parts[2]));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = Jwts.builder().subject("client@example.test")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(key).compact();
        assertThrows(JwtException.class, () -> tokens.parse(expired));
    }

    @Test
    void rejectsMalformedTokens() {
        assertThrows(JwtException.class, () -> tokens.parse("not-a-jwt"));
        assertThrows(JwtException.class, () -> tokens.parse("a.b.c"));
        assertThrows(IllegalArgumentException.class, () -> tokens.parse(""));
    }

    @Test
    void rejectsUnsignedTokenWithNoneAlgorithm() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin@example.test\",\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> tokens.parse(header + "." + payload + "."));
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String noExp = Jwts.builder().subject("client@example.test").signWith(key).compact();
        assertThrows(JwtException.class, () -> tokens.parse(noExp));
    }
}
