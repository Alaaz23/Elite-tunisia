package tn.elitetunisia.common.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Démarre l'application avec le profil de production réel (application-prod.yml) en plus du profil `test`
 * qui fournit seulement le moteur H2 : les valeurs du profil prod l'emportent car il est activé en dernier.
 * Les variables obligatoires reçoivent des valeurs factices propres au test.
 *
 * Le jeton CSRF est obtenu par la vraie poignée de main (GET /api/auth/csrf puis cookie + en-tête) :
 * le post-processeur de test csrf() remplace le dépôt de jetons du filtre pour tout le contexte
 * et masquerait justement les cookies que ce test vérifie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test,prod",
        "DATABASE_URL=jdbc:h2:mem:prod_profile_check;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "DATABASE_USERNAME=sa",
        "DATABASE_PASSWORD=",
        "JWT_SECRET=prod-profile-check-secret-0123456789-abcdefghijklmnop",
        "CORS_ALLOWED_ORIGINS=https://app.example.test" })
@AutoConfigureMockMvc
class ProductionProfileHardeningTest {
    @Autowired MockMvc mvc;
    @org.springframework.boot.test.web.server.LocalServerPort int port;

    private MockHttpServletRequestBuilder withBrowserCsrf(MockHttpServletRequestBuilder request) throws Exception {
        var cookie = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertTrue(cookie != null, "le cookie CSRF doit être émis");
        return request.cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue());
    }

    @Test
    void apiDescriptionIsNotPublic() throws Exception {
        mvc.perform(get("/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/api-docs/swagger-config")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void onlyHealthIsExposedByActuator() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        for (String endpoint : new String[] { "env", "beans", "heapdump", "configprops", "loggers", "mappings" }) {
            mvc.perform(get("/actuator/" + endpoint)).andExpect(status().is4xxClientError());
        }
    }

    @Test
    void cookiesAreSecureAndSameSiteStrict() throws Exception {
        var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String csrfCookie = csrf.getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertTrue(csrfCookie.contains("Secure"), csrfCookie.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=<masqué>"));

        String email = "prod-" + UUID.randomUUID() + "@example.test";
        String password = "Production-Strong-123!";
        var result = mvc.perform(withBrowserCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"firstName\":\"Prod\",\"lastName\":\"Check\",\"email\":\"%s\",\"phone\":\"+216 20 123 456\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}", email, password, password)))
                .andExpect(status().isCreated()).andReturn();
        String refreshCookie = result.getResponse().getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("elite_refresh=")).findFirst().orElseThrow();
        assertTrue(refreshCookie.contains("Secure") && refreshCookie.contains("HttpOnly") && refreshCookie.contains("SameSite=Strict"),
                refreshCookie.replaceAll("elite_refresh=[^;]*", "elite_refresh=<masqué>"));
    }

    /** En-têtes bruts sur un vrai serveur : MockMvc reconstruit les cookies et ne reflète pas toujours leurs attributs. */
    @Test
    void csrfCookieCarriesSecureAndSameSiteStrictOnTheWire() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/auth/csrf")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        String csrfCookie = response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        String masked = csrfCookie.replaceAll("XSRF-TOKEN=[^;]*", "XSRF-TOKEN=<masqué>");
        assertTrue(csrfCookie.contains("Secure"), masked);
        assertTrue(csrfCookie.contains("SameSite=Strict"), masked);
        assertTrue(csrfCookie.contains("Path=/api"), masked);
    }

    @Test
    void writesWithoutTheCsrfHandshakeAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@example.test\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedResponsesDoNotDeleteTheCsrfCookie() throws Exception {
        String email = "csrf-" + UUID.randomUUID() + "@example.test";
        String password = "Production-Strong-123!";
        var registered = mvc.perform(withBrowserCsrf(post("/api/auth/register")).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"firstName\":\"Csrf\",\"lastName\":\"Check\",\"email\":\"%s\",\"phone\":\"+216 20 123 456\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}", email, password, password)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(registered);
        assertTrue(matcher.find(), "le jeton d'accès doit être renvoyé");
        String token = matcher.group(1);
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var response = mvc.perform(get("/api/auth/me").cookie(csrf).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(response.getHeaders("Set-Cookie").stream().noneMatch(c -> c.startsWith("XSRF-TOKEN=;") || c.contains("Max-Age=0")),
                "une réponse authentifiée ne doit pas supprimer le cookie CSRF");
    }

    @Test
    void errorResponsesDoNotLeakInternals() throws Exception {
        var body = mvc.perform(withBrowserCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x\""))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Exception") || body.contains("at tn.elitetunisia") || body.contains("com.fasterxml") || body.contains("tools.jackson"), body);
    }
}
