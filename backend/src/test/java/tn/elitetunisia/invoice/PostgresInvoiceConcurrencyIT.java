package tn.elitetunisia.invoice;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.quote.QuoteService;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
@Tag("postgres")
public class PostgresInvoiceConcurrencyIT {
    static {
        tn.elitetunisia.PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired InvoiceService invoiceService;
    @Autowired InvoiceRepository invoices;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quotes;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void requirePostgres() throws Exception {
        tn.elitetunisia.PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    @Test
    public void concurrentInvoiceCreationProducesDistinctReferences() throws Exception {
        var f = fixtures.createBooking(24);
        var quoteReq = new tn.elitetunisia.quote.QuoteRequest(java.time.Instant.now().plusSeconds(10*86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false, java.math.BigDecimal.ZERO, "", "", java.util.List.of());
        var quote = quotes.create(f.booking().id(), quoteReq, f.owner(), null);
        quotes.send(quote.id(), f.owner(), null);
        quotes.customerAction(quote.id(), f.owner(), "accept", null, null);

        int threads = 6;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        Callable<String> task = () -> {
            start.await();
            var inv = invoiceService.createFromQuote(quote.id(), f.owner(), 14);
            return inv.getReference();
        };

        Future<String>[] futures = new Future[threads];
        for (int i = 0; i < threads; i++) futures[i] = ex.submit(task);
        start.countDown();

        java.util.Set<String> refs = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < threads; i++) {
            try { refs.add(futures[i].get(30, TimeUnit.SECONDS)); } catch (Exception e) { /* record if needed */ }
        }
        ex.shutdownNow();

        // Expect as many distinct references as attempts (creating twice from same quote allowed)
        assertEquals(threads, refs.size(), "Each concurrent creation should produce a distinct invoice reference");
    }
}
