package tn.elitetunisia.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.invoice.InvoiceService;
import tn.elitetunisia.quote.QuoteRequest;
import tn.elitetunisia.quote.QuoteService;

/**
 * BOLA/IDOR via de vraies requêtes HTTP : un client B ne lit ni ne modifie les ressources du client A.
 * Les fichiers publics (/api/files/**) ne sont pas couverts : ils sont accessibles sans authentification par conception.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossCustomerAccessHttpTest {

    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    private static final List<String> CUSTOMER_AUTHORITIES = List.of(
            "bookings:read", "bookings:cancel", "quotes:read", "quotes:accept", "quotes:reject",
            "payments:create", "invoices:read");

    @Autowired MockMvc mvc;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quotes;
    @Autowired InvoiceService invoices;
    @Autowired UserRepository users;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private RequestPostProcessor customer(UUID userId) {
        var granted = new ArrayList<SimpleGrantedAuthority>();
        granted.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        CUSTOMER_AUTHORITIES.forEach(a -> granted.add(new SimpleGrantedAuthority(a)));
        return SecurityMockMvcRequestPostProcessors.user(users.findById(userId).orElseThrow().getEmail()).authorities(granted);
    }

    private UUID sentQuoteFor(TestFixtures.Fixture fixture) {
        var request = new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(ChronoUnit.MICROS), false, BigDecimal.ZERO, "", "", List.of());
        var quote = quotes.create(fixture.booking().id(), request, fixture.owner(), null);
        quotes.send(quote.id(), fixture.owner(), null);
        return quote.id();
    }

    private String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    @Test
    void anotherCustomerCannotReadOrCancelABooking() throws Exception {
        var owner = fixtures.createBooking(24);
        var stranger = fixtures.createBooking(24);
        String bookingPath = "/api/bookings/" + owner.booking().id();

        mvc.perform(get(bookingPath).with(customer(owner.owner()))).andExpect(status().isOk());
        mvc.perform(get(bookingPath).with(customer(stranger.owner()))).andExpect(status().isNotFound());

        String before = body(mvc.perform(get(bookingPath).with(customer(owner.owner()))));
        mvc.perform(post(bookingPath + "/cancel").with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(customer(stranger.owner()))).andExpect(status().isNotFound());
        String after = body(mvc.perform(get(bookingPath).with(customer(owner.owner()))));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void anotherCustomerCannotReadOrAnswerAQuote() throws Exception {
        var owner = fixtures.createBooking(24);
        var stranger = fixtures.createBooking(24);
        String quotePath = "/api/customer/quotes/" + sentQuoteFor(owner);

        mvc.perform(get(quotePath).with(customer(owner.owner()))).andExpect(status().isOk());
        mvc.perform(get(quotePath).with(customer(stranger.owner()))).andExpect(status().isNotFound());

        String before = body(mvc.perform(get(quotePath).with(customer(owner.owner()))));
        for (String action : List.of("/accept", "/reject", "/request-change")) {
            mvc.perform(post(quotePath + action).with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}")
                    .with(customer(stranger.owner()))).andExpect(status().isNotFound());
        }
        String after = body(mvc.perform(get(quotePath).with(customer(owner.owner()))));
        assertThat(after).isEqualTo(before);
    }

    @Test
    void anotherCustomerCannotPayAQuote() throws Exception {
        var owner = fixtures.createBooking(24);
        var stranger = fixtures.createBooking(24);
        UUID quoteId = sentQuoteFor(owner);
        quotes.customerAction(quoteId, owner.owner(), "accept", null, null);
        String paymentsPath = "/api/customer/quotes/" + quoteId + "/payments";

        String strangerBody = "{\"amount\":100,\"method\":\"CARD\",\"idempotencyKey\":\"stranger-" + UUID.randomUUID() + "\"}";
        mvc.perform(post(paymentsPath).with(SecurityMockMvcRequestPostProcessors.csrf()).with(customer(stranger.owner()))
                .contentType(MediaType.APPLICATION_JSON).content(strangerBody)).andExpect(status().isNotFound());

        String ownerBody = "{\"amount\":" + jdbc.queryForObject("select total_amount from quotes where id = ?", BigDecimal.class, quoteId) + ",\"method\":\"CARD\",\"idempotencyKey\":\"owner-" + UUID.randomUUID() + "\"}";
        mvc.perform(post(paymentsPath).with(SecurityMockMvcRequestPostProcessors.csrf()).with(customer(owner.owner()))
                .contentType(MediaType.APPLICATION_JSON).content(ownerBody)).andExpect(status().isOk());
    }

    @Test
    void anotherCustomerCannotReadAnInvoiceOrItsPdf() throws Exception {
        var owner = fixtures.createBooking(24);
        var stranger = fixtures.createBooking(24);
        UUID quoteId = sentQuoteFor(owner);
        quotes.customerAction(quoteId, owner.owner(), "accept", null, null);
        UUID invoiceId = invoices.createFromQuote(quoteId, owner.owner(), 7).getId();

        mvc.perform(get("/api/invoices/" + invoiceId).with(customer(owner.owner()))).andExpect(status().isOk());
        mvc.perform(get("/api/invoices/" + invoiceId).with(customer(stranger.owner()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/invoices/" + invoiceId + "/pdf").with(customer(stranger.owner()))).andExpect(status().isNotFound());
    }

    @Test
    void listsOnlyReturnTheCallersOwnBookings() throws Exception {
        var owner = fixtures.createBooking(24);
        var stranger = fixtures.createBooking(24);

        String ownerList = body(mvc.perform(get("/api/bookings").with(customer(owner.owner()))));
        String strangerList = body(mvc.perform(get("/api/bookings").with(customer(stranger.owner()))));
        assertThat(ownerList).contains(owner.booking().id().toString());
        assertThat(strangerList).doesNotContain(owner.booking().id().toString());
    }
}
