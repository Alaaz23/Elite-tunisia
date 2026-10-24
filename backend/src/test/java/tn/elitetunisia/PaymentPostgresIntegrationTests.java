package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tn.elitetunisia.payment.PaymentEntity;
import tn.elitetunisia.payment.PaymentRepository;
import tn.elitetunisia.payment.PaymentService;
import tn.elitetunisia.payment.PaymentMethod;
import tn.elitetunisia.quote.QuoteService;
import tn.elitetunisia.quote.QuoteRepository;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class PaymentPostgresIntegrationTests {
static {
    if (!PostgresTestHelper.configurePostgresIfRequested()) {
        System.setProperty("spring.profiles.active", "test");
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:elite_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        System.setProperty("spring.datasource.username", "sa");
            System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
            System.setProperty("spring.flyway.enabled", "false");
            System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        }
    }
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quoteService;
    @Autowired QuoteRepository quoteRepo;
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository payments;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired tn.elitetunisia.booking.BookingService bookings;

    @org.junit.jupiter.api.BeforeEach void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    @Test void paymentCapturedAndIdempotent() {
        var f = fixtures.createBooking(24);
        var quoteReq = new tn.elitetunisia.quote.QuoteRequest(java.time.Instant.now().plusSeconds(10*86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false, BigDecimal.ZERO, "", "", java.util.List.of());
        var quote = quoteService.create(f.booking().id(), quoteReq, f.owner(), null);
        quoteService.send(quote.id(), f.owner(), null);
        quoteService.customerAction(quote.id(), f.owner(), "accept", null, null);

        String key = "itest-key-" + UUID.randomUUID();
        PaymentEntity first = paymentService.createForQuote(quote.id(), f.owner(), quote.totalAmount(), PaymentMethod.CARD, key);
        assertEquals(tn.elitetunisia.payment.PaymentStatus.CAPTURED, first.getStatus());

        PaymentEntity second = paymentService.createForQuote(quote.id(), f.owner(), quote.totalAmount(), PaymentMethod.CARD, key);
        assertEquals(first.getId(), second.getId());

        var stored = payments.findByIdempotencyKey(key).orElseThrow();
        assertEquals(first.getId(), stored.getId());

        assertEquals(f.owner(), stored.getCustomerId());
        assertEquals(quote.totalAmount(), stored.getAmount());
        assertEquals(f.booking().id(), stored.getBooking().getId());
        assertEquals(2, jdbc.queryForObject("select count(*) from payment_events where payment_id = ?", Integer.class, first.getId()));
        var bookingSummary = bookings.adminGet(f.booking().id());
        assertEquals(tn.elitetunisia.booking.BookingStatus.PENDING, bookingSummary.status());
    }

    @Test void paymentRejectedWhenQuoteNotAccepted() {
        var f = fixtures.createBooking(24);
        var quoteReq = new tn.elitetunisia.quote.QuoteRequest(java.time.Instant.now().plusSeconds(10*86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false, BigDecimal.ZERO, "", "", java.util.List.of());
        var quote = quoteService.create(f.booking().id(), quoteReq, f.owner(), null);
        // do not accept
        String key = "itest-key-" + UUID.randomUUID();
        var ex = assertThrows(RuntimeException.class, () -> paymentService.createForQuote(quote.id(), f.owner(), quote.totalAmount(), PaymentMethod.CARD, key));
        assertTrue(ex.getMessage().contains("QUOTE_NOT_ACCEPTED") || ex.getMessage().contains("QUOTE_NOT_ACCEPTED"));
        assertTrue(payments.findByIdempotencyKey(key).isEmpty());
    }

    @Test void paymentApiUsesAuthenticatedOwnerAndRejectsForgedAmountAndForeignAccess() throws Exception {
        var fixture = fixtures.createBooking(24);
        var quote = acceptedQuote(fixture);
        String email = jdbc.queryForObject("select email from users where id = ?", String.class, fixture.owner());
        String key = "api-key-" + UUID.randomUUID();
        String url = "/api/customer/quotes/" + quote.id() + "/payments";
        var body = new java.util.HashMap<String, Object>();
        body.put("amount", quote.totalAmount()); body.put("method", "CARD"); body.put("idempotencyKey", key);
        var owner = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email)
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("payments:create"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(owner)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CAPTURED"));
        assertEquals(fixture.owner(), payments.findByIdempotencyKey(key).orElseThrow().getCustomerId());
        body.put("amount", BigDecimal.ONE); body.put("idempotencyKey", "forged-" + UUID.randomUUID());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(owner)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        assertTrue(payments.findByIdempotencyKey((String) body.get("idempotencyKey")).isEmpty());
        var other = fixtures.createBooking(24);
        String otherEmail = jdbc.queryForObject("select email from users where id = ?", String.class, other.owner());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(otherEmail)
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("payments:create")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(email).roles("CUSTOMER"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test void sameIdempotencyKeyCannotPayAnotherQuote() {
        var fixture = fixtures.createBooking(24);
        var first = acceptedQuote(fixture);
        String key = "conflict-" + UUID.randomUUID();
        var payment = paymentService.createForQuote(first.id(), fixture.owner(), first.totalAmount(), PaymentMethod.CARD, key);
        var other = fixtures.createBooking(24);
        var second = acceptedQuote(other);
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(tn.elitetunisia.payment.PaymentException.class,
                () -> paymentService.createForQuote(second.id(), other.owner(), second.totalAmount(), PaymentMethod.CARD, key)).getCode());
        assertEquals(payment.getId(), payments.findByIdempotencyKey(key).orElseThrow().getId());
        assertEquals(2, jdbc.queryForObject("select count(*) from payment_events where payment_id = ?", Integer.class, payment.getId()));
    }

    @Test void concurrentIdenticalPaymentsHaveOnePersistentEffect() throws Exception {
        var fixture = fixtures.createBooking(24);
        var quote = acceptedQuote(fixture);
        String key = "concurrent-" + UUID.randomUUID();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<UUID> operation = () -> {
                ready.countDown();
                assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return paymentService.createForQuote(quote.id(), fixture.owner(), quote.totalAmount(), PaymentMethod.CARD, key).getId();
            };
            var first = executor.submit(operation);
            var second = executor.submit(operation);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            UUID paymentId = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(paymentId, second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("select count(*) from payments where idempotency_key = ?", Integer.class, key));
            assertEquals(2, jdbc.queryForObject("select count(*) from payment_events where payment_id = ?", Integer.class, paymentId));
            assertEquals(tn.elitetunisia.booking.BookingStatus.PENDING, bookings.adminGet(fixture.booking().id()).status());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private tn.elitetunisia.quote.QuoteSummary acceptedQuote(TestFixtures.Fixture fixture) {
        var request = new tn.elitetunisia.quote.QuoteRequest(java.time.Instant.now().plusSeconds(10 * 86400L)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS), false, BigDecimal.ZERO, "", "", java.util.List.of());
        var quote = quoteService.create(fixture.booking().id(), request, fixture.owner(), null);
        quoteService.send(quote.id(), fixture.owner(), null);
        return quoteService.customerAction(quote.id(), fixture.owner(), "accept", null, null);
    }
}
