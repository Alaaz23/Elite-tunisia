package tn.elitetunisia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestIdentities;

/**
 * Parcours HTTP réels du refresh token et du logout : statuts, cookies et effets en base.
 * La révocation est limitée au jeton présenté au logout (pas de révocation de famille).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRefreshLogoutHttpTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final String PASSWORD = "Refresh-Strong-123!";
    private static final String COOKIE = "elite_refresh";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String register() throws Exception {
        String email = TestIdentities.email("refresh-http");
        MvcResult result = mvc.perform(post("/api/auth/register").with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"firstName\":\"Refresh\",\"lastName\":\"Client\",\"email\":\"%s\",\"phone\":\"+216 20 123 456\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}", email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return cookieValue(result);
    }

    private static String cookieValue(MvcResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(COOKIE + "=")) {
                int end = header.indexOf(';');
                return header.substring(COOKIE.length() + 1, end < 0 ? header.length() : end);
            }
        }
        return null;
    }

    private MvcResult refresh(String token) throws Exception {
        var request = post("/api/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf());
        if (token != null) request.cookie(new jakarta.servlet.http.Cookie(COOKIE, token));
        return mvc.perform(request).andReturn();
    }

    private MvcResult logout(String token) throws Exception {
        var request = post("/api/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf());
        if (token != null) request.cookie(new jakarta.servlet.http.Cookie(COOKIE, token));
        return mvc.perform(request).andReturn();
    }

    private static String sha256Hex(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean isRevoked(String token) throws Exception {
        Instant revokedAt = jdbc.queryForObject("SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
                (rs, n) -> rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                sha256Hex(token));
        return revokedAt != null;
    }

    private void expire(String token) throws Exception {
        jdbc.update("UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), sha256Hex(token));
    }

    @Test
    void validRefreshRotatesTheTokenAndSetsANewCookie() throws Exception {
        String first = register();
        MvcResult result = refresh(first);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String second = cookieValue(result);
        assertThat(second).isNotNull().isNotEqualTo(first);
        assertThat(isRevoked(first)).isTrue();
        assertThat(isRevoked(second)).isFalse();
    }

    @Test
    void expiredRefreshTokenIsRejectedAndRevoked() throws Exception {
        String token = register();
        expire(token);
        mvc.perform(post("/api/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie(COOKIE, token)))
                .andExpect(status().isUnauthorized());
        assertThat(isRevoked(token)).isTrue();
    }

    @Test
    void revokedRefreshTokenIsRejected() throws Exception {
        String token = register();
        logout(token);
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void reusingAnOldTokenAfterRotationRevokesTheWholeFamily() throws Exception {
        String first = register();
        String second = cookieValue(refresh(first));
        assertThat(second).isNotNull();

        MvcResult replay = refresh(first);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);

        MvcResult legitimate = refresh(second);
        assertThat(legitimate.getResponse().getStatus()).isEqualTo(401);
        assertThat(isRevoked(second)).isTrue();
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        assertThat(refresh(null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithMalformedCookieIsRejectedWithoutServerError() throws Exception {
        register();
        for (String garbage : List.of("not-a-token", "%%%", "A".repeat(200))) {
            int status = refresh(garbage).getResponse().getStatus();
            assertThat(status).as("cookie=" + garbage).isEqualTo(401);
        }
    }

    @Test
    void refreshAfterLogoutIsRejected() throws Exception {
        String token = register();
        MvcResult out = logout(token);
        assertThat(out.getResponse().getStatus()).isEqualTo(204);
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void simultaneousRefreshesNeverIssueTwoValidSuccessors() throws Exception {
        String token = register();
        int parallel = 2;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return refresh(token);
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> successors = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                MvcResult r = f.get();
                statuses.add(r.getResponse().getStatus());
                if (r.getResponse().getStatus() == 200) successors.add(cookieValue(r));
            }
            assertThat(statuses).allMatch(s -> s == 200 || s == 401);
            assertThat(successors.size()).isLessThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void logoutClearsTheCookieAndRevokesOnlyThePresentedToken() throws Exception {
        String token = register();
        mvc.perform(post("/api/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie(COOKIE, token)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0))
                .andExpect(cookie().value(COOKIE, ""));
        assertThat(isRevoked(token)).isTrue();
    }

    @Test
    void logoutWithoutCookieReturnsNoContentAndClearsCookie() throws Exception {
        mvc.perform(post("/api/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void logoutWithExpiredTokenStillClearsCookieAndRejectsLaterRefresh() throws Exception {
        String token = register();
        expire(token);
        mvc.perform(post("/api/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie(COOKIE, token)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutWithMalformedCookieReturnsNoContentWithoutServerError() throws Exception {
        register();
        mvc.perform(post("/api/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie(COOKIE, "%%%")))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutTwiceWithTheSameTokenIsIdempotent() throws Exception {
        String token = register();
        assertThat(logout(token).getResponse().getStatus()).isEqualTo(204);
        assertThat(logout(token).getResponse().getStatus()).isEqualTo(204);
        assertThat(isRevoked(token)).isTrue();
    }

    @Test
    void refreshResponseBodyDoesNotEchoTheRefreshToken() throws Exception {
        String token = register();
        MvcResult result = refresh(token);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
        mvc.perform(post("/api/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf())
                .cookie(new jakarta.servlet.http.Cookie(COOKIE, cookieValue(result))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
