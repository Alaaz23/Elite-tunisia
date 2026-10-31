package tn.elitetunisia.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;

/** Les URLs d'images sont seulement stockées puis rendues par img src : les schémas exécutables doivent être refusés. */
class VehicleImageUrlValidationTest {
    private static int violations(String url) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(new VehicleImageRequest(url, "alt", (short) 0, false)).size();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://cdn.example.com/a.jpg", "http://example.com/a.png", "/api/files/0f0e1d2c-aaaa-bbbb-cccc-0123456789ab.webp"})
    void acceptsHttpAndLocalFileUrls(String url) {
        assertEquals(0, violations(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html;base64,PHNjcmlwdD4=", "file:///etc/passwd",
            "//evil.example/a.png", "ftp://example.com/a.png", "https://exa mple.com/a.png", "/api/files/a/b.png",
            "/other/path.png", "https://example.com/a.png\nX: y"})
    void rejectsExecutableSchemesAndMalformedValues(String url) {
        assertEquals(1, violations(url));
    }
}
