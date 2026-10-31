package tn.elitetunisia.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.auth.repository.UserRepository;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.quote.QuoteRequest;
import tn.elitetunisia.quote.QuoteService;

/** Isolation des factures entre clients et erreurs typées (HTTP 404, jamais 500). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceAccessControlTest {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired MockMvc mvc;
    @Autowired InvoiceService invoices;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quotes;
    @Autowired UserRepository users;

    private InvoiceEntity invoiceFor(TestFixtures.Fixture fixture) {
        var request = new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(ChronoUnit.MICROS), false, BigDecimal.ZERO, "", "", List.of());
        var quote = quotes.create(fixture.booking().id(), request, fixture.owner(), null);
        quotes.send(quote.id(), fixture.owner(), null);
        quotes.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        return invoices.createFromQuote(quote.id(), fixture.owner(), 7);
    }

    private RequestPostProcessor customer(UUID userId) {
        return SecurityMockMvcRequestPostProcessors.user(users.findById(userId).orElseThrow().getEmail())
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"), new SimpleGrantedAuthority("invoices:read"));
    }

    private RequestPostProcessor admin(String... authorities) {
        var granted = new java.util.ArrayList<SimpleGrantedAuthority>();
        granted.add(new SimpleGrantedAuthority("admin:access"));
        for (String a : authorities) granted.add(new SimpleGrantedAuthority(a));
        return SecurityMockMvcRequestPostProcessors.user("invoice-admin@example.test").authorities(granted);
    }

    @Test
    void aCustomerOnlySeesOwnInvoicesAndOthersAreIndistinguishableFromMissingOnes() throws Exception {
        var owner = fixtures.createBooking(24);
        var stranger = fixtures.createBooking(24);
        var invoice = invoiceFor(owner);

        mvc.perform(get("/api/invoices/" + invoice.getId()).with(customer(owner.owner()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoice.getId().toString()));
        mvc.perform(get("/api/invoices").with(customer(stranger.owner()))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        String foreign = mvc.perform(get("/api/invoices/" + invoice.getId()).with(customer(stranger.owner())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND")).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(get("/api/invoices/" + UUID.randomUUID()).with(customer(stranger.owner())))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreign.replaceAll("\"instance\":\"[^\"]*\"", ""), unknown.replaceAll("\"instance\":\"[^\"]*\"", ""), "aucune différence observable entre facture étrangère et facture inconnue");
        mvc.perform(get("/api/invoices/" + invoice.getId() + "/pdf").with(customer(stranger.owner()))).andExpect(status().isNotFound());
    }

    @Test
    void unknownInvoicesAreNotFoundForStaffIncludingThePdf() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/admin/invoices/" + unknown).with(admin("invoices:read"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
        mvc.perform(get("/api/admin/invoices/" + unknown + "/pdf").with(admin("invoices:read"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    }

    @Test
    void staffWithoutInvoiceReadAndCustomersCannotUseTheAdministrationEndpoints() throws Exception {
        var owner = fixtures.createBooking(24);
        var invoice = invoiceFor(owner);
        for (String path : new String[] { "/api/admin/invoices", "/api/admin/invoices/" + invoice.getId(), "/api/admin/invoices/" + invoice.getId() + "/pdf" }) {
            mvc.perform(get(path).with(admin("bookings:read", "bookings:manage"))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(customer(owner.owner()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void invoiceDetailAndPdfAreReadableByTheOwnerAndByAuthorizedStaff() throws Exception {
        // Régression : open-in-view est désactivé ; le détail (avec lignes) et le PDF levaient une LazyInitializationException (HTTP 500).
        var owner = fixtures.createBooking(24);
        var invoice = invoiceFor(owner);
        mvc.perform(get("/api/invoices/" + invoice.getId()).with(customer(owner.owner()))).andExpect(status().isOk()).andExpect(jsonPath("$.lines").isNotEmpty());
        mvc.perform(get("/api/admin/invoices/" + invoice.getId()).with(admin("invoices:read"))).andExpect(status().isOk()).andExpect(jsonPath("$.lines").isNotEmpty());
        byte[] customerPdf = mvc.perform(get("/api/invoices/" + invoice.getId() + "/pdf").with(customer(owner.owner())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] adminPdf = mvc.perform(get("/api/admin/invoices/" + invoice.getId() + "/pdf").with(admin("invoices:read")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(customerPdf, 0, 4));
        assertEquals("%PDF", new String(adminPdf, 0, 4));
    }

    @Test
    void serviceLayerRaisesTypedErrors() {
        assertEquals("INVOICE_NOT_FOUND", org.junit.jupiter.api.Assertions.assertThrows(DomainException.class, () -> invoices.adminGet(UUID.randomUUID())).getCode());
        assertEquals("QUOTE_NOT_FOUND", org.junit.jupiter.api.Assertions.assertThrows(DomainException.class, () -> invoices.createFromQuote(UUID.randomUUID(), UUID.randomUUID(), 7)).getCode());
    }
}
