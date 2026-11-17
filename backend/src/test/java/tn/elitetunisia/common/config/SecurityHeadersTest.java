package tn.elitetunisia.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHeadersTest {
    private static final String CSP_REPORT_ONLY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";
    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Autowired MockMvc mvc;

    @Test
    void publicApiResponsesCarryApiSecurityHeaders() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy-Report-Only", CSP_REPORT_ONLY))
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", PERMISSIONS_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void unauthenticatedErrorResponsesCarryApiSecurityHeaders() throws Exception {
        mvc.perform(get("/api/admin/security-headers-probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy-Report-Only", CSP_REPORT_ONLY))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", PERMISSIONS_POLICY));
    }
}
