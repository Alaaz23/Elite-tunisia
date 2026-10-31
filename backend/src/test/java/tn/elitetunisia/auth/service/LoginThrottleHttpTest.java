package tn.elitetunisia.auth.service;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tn.elitetunisia.PostgresTestHelper;

/** Parcours HTTP réel : l'échec répété de connexion est freiné, sans bloquer d'autres comptes ni d'autres adresses. */
@SpringBootTest(properties = "app.security.login-throttle.max-failures=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginThrottleHttpTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final String PASSWORD = "Throttle-Strong-123!";

    @Autowired MockMvc mvc;

    private static RequestPostProcessor from(String address) {
        return request -> { request.setRemoteAddr(address); return request; };
    }

    private String register() throws Exception {
        String email = "throttle-" + UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/auth/register").with(SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"firstName\":\"Throttle\",\"lastName\":\"Client\",\"email\":\"%s\",\"phone\":\"+216 20 123 456\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}", email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password, String address) throws Exception {
        return mvc.perform(post("/api/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf()).with(from(address))
                .contentType(MediaType.APPLICATION_JSON).content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)));
    }

    @Test
    void repeatedFailuresAreThrottledEvenForTheRightPassword() throws Exception {
        String email = register();
        for (int i = 0; i < 3; i++) {
            login(email, "Wrong-Password-1!", "10.1.1.1").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, "10.1.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"))
                .andExpect(jsonPath("$.detail").value(containsString("Réessayez")));
    }

    @Test
    void throttlingDoesNotLockOutTheAccountFromAnotherAddressOrAffectOtherAccounts() throws Exception {
        String victim = register();
        String other = register();
        for (int i = 0; i < 3; i++) login(victim, "Wrong-Password-1!", "10.2.2.2").andExpect(status().isUnauthorized());
        login(victim, PASSWORD, "10.2.2.2").andExpect(status().isTooManyRequests());
        login(victim, PASSWORD, "10.2.2.3").andExpect(status().isOk());
        login(other, PASSWORD, "10.2.2.2").andExpect(status().isOk());
    }

    @Test
    void aSuccessfulLoginResetsThePreviousFailures() throws Exception {
        String email = register();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 2; i++) login(email, "Wrong-Password-1!", "10.3.3.3").andExpect(status().isUnauthorized());
            login(email, PASSWORD, "10.3.3.3").andExpect(status().isOk());
        }
    }
}
