package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tn.elitetunisia.invoice.InvoiceEntity;
import tn.elitetunisia.invoice.InvoiceRepository;
import tn.elitetunisia.invoice.InvoiceService;
import tn.elitetunisia.quote.QuoteService;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class InvoicePostgresIntegrationTests {
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
    @Autowired InvoiceService invoiceService;
    @Autowired InvoiceRepository invoices;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    @Test void createInvoiceFromAcceptedQuote() {
        var f = fixtures.createBooking(24);
        var quoteReq = new tn.elitetunisia.quote.QuoteRequest(java.time.Instant.now().plusSeconds(10*86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false, java.math.BigDecimal.ZERO, "", "", java.util.List.of());
        var quote = quoteService.create(f.booking().id(), quoteReq, f.owner(), null);
        quoteService.send(quote.id(), f.owner(), null);
        quoteService.customerAction(quote.id(), f.owner(), "accept", null, null);

        InvoiceEntity inv = invoiceService.createFromQuote(quote.id(), f.owner(), 14);
        assertNotNull(inv.getReference());
        assertEquals(quote.totalAmount(), inv.getTotalAmount());
        var stored = invoices.findByReference(inv.getReference()).orElseThrow();
        assertEquals(inv.getId(), stored.getId());
    }

    @Test void invoiceReferenceUnique() {
        var f = fixtures.createBooking(24);
        var quoteReq = new tn.elitetunisia.quote.QuoteRequest(java.time.Instant.now().plusSeconds(10*86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false, java.math.BigDecimal.ZERO, "", "", java.util.List.of());
        var quote = quoteService.create(f.booking().id(), quoteReq, f.owner(), null);
        quoteService.send(quote.id(), f.owner(), null);
        quoteService.customerAction(quote.id(), f.owner(), "accept", null, null);

        InvoiceEntity a = invoiceService.createFromQuote(quote.id(), f.owner(), 14);
        InvoiceEntity b = invoiceService.createFromQuote(quote.id(), f.owner(), 14);
        // creating twice from same quote is allowed but should produce distinct references
        assertNotEquals(a.getReference(), b.getReference());
    }
}
