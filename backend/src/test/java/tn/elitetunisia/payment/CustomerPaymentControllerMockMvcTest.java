package tn.elitetunisia.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class CustomerPaymentControllerMockMvcTest {
    @Autowired MockMvc mvc;

    @TestConfiguration
    static class Cfg {
        @Bean public PaymentService paymentService() { return mock(PaymentService.class); }
        @Bean public tn.elitetunisia.user.service.AccountService accountService() { return mock(tn.elitetunisia.user.service.AccountService.class); }
    }

    @Autowired PaymentService svc;
    @Autowired tn.elitetunisia.user.service.AccountService accounts;

    @Test
    public void unauthenticatedIsForbidden() throws Exception {
        mvc.perform(post("/api/customer/quotes/" + UUID.randomUUID() + "/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    public void missingIdempotencyRejected() throws Exception {
        var quote = UUID.randomUUID();
        mvc.perform(post("/api/customer/quotes/" + quote + "/payments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("c@t.com").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100.0,\"method\":\"CARD\",\"idempotencyKey\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void successfulPaymentReturnsSummary() throws Exception {
        UUID q = UUID.randomUUID(); UUID pid = UUID.randomUUID();
        when(accounts.current(eq("user@t"))).thenReturn(new tn.elitetunisia.user.dto.UserSummary(pid, null, null, "user@t", null, null, java.util.Set.of(), "ACTIVE"));
        var payment = new PaymentEntity(null, pid, new BigDecimal("100.00"), "TND", PaymentMethod.CARD, "k3");
        payment.setTransactionRef("TX-1"); payment.setStatus(PaymentStatus.CAPTURED);
        when(svc.createForQuote(eq(q), eq(pid), any(), eq(PaymentMethod.CARD), eq("k3"))).thenReturn(payment);

        mvc.perform(post("/api/customer/quotes/" + q + "/payments").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("user@t").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"), new org.springframework.security.core.authority.SimpleGrantedAuthority("payments:create"))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100.00,\"method\":\"CARD\",\"idempotencyKey\":\"k3\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CAPTURED"));
    }
}
