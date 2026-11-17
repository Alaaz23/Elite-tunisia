package tn.elitetunisia.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestIdentities;

/** Le cookie refresh suit exactement app.security.jwt.refresh-token-days et garde ses attributs de sécurité. */
@SpringBootTest(properties = "app.security.jwt.refresh-token-days=7")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshCookieAttributesTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final String PASSWORD = "Cookie-Strong-123!";

    @Autowired MockMvc mvc;

    @Test
    void refreshCookieMaxAgeMatchesConfiguredLifetimeAndKeepsSecurityAttributes() throws Exception {
        String email = TestIdentities.email("cookie");
        mvc.perform(post("/api/auth/register").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"firstName\":\"Cookie\",\"lastName\":\"Client\",\"email\":\"%s\",\"phone\":\"+216 20 123 456\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}", email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("elite_refresh=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=604800")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", not(containsString("Max-Age=2592000"))));
    }
}
