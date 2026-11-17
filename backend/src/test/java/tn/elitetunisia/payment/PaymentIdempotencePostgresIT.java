package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tn.elitetunisia.PostgresTestHelper;
import tn.elitetunisia.TestFixtures;
import tn.elitetunisia.quote.QuoteRequest;
import tn.elitetunisia.quote.QuoteService;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
@Tag("postgres")
public class PaymentIdempotencePostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired PaymentService paymentService;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quotes;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    private record Accepted(UUID quoteId, UUID owner, java.math.BigDecimal total) {}

    private Accepted acceptedQuote() {
        var fixture = fixtures.createBooking(24);
        var request = new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(ChronoUnit.MICROS), false,
                java.math.BigDecimal.ZERO, "", "", List.of());
        var quote = quotes.create(fixture.booking().id(), request, fixture.owner(), null);
        quotes.send(quote.id(), fixture.owner(), null);
        quotes.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        return new Accepted(quote.id(), fixture.owner(), quote.totalAmount());
    }

    @Test
    public void sameKeyConcurrentRequests_createSingleRowAndSingleCapture() throws Exception {
        var q = acceptedQuote();
        String key = "same-key-" + UUID.randomUUID();
        int threads = 6;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(ex.submit(() -> {
                    start.await();
                    try {
                        return paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key).getId().toString();
                    } catch (PaymentException e) {
                        return "ERR:" + e.getCode();
                    }
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : futures) {
                String o = f.get(60, TimeUnit.SECONDS);
                outcomes.add(o);
                if (!o.startsWith("ERR:")) ids.add(o);
            }
            long rows = jdbc.queryForObject(
                    "SELECT count(*) FROM payments WHERE quote_id = ? AND idempotency_key = ?", Long.class, q.quoteId(), key);
            long captured = jdbc.queryForObject(
                    "SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", Long.class, q.quoteId());
            assertEquals(1L, rows, "Une seule ligne pour la clé. Résultats : " + outcomes);
            assertEquals(1, ids.size(), "Toutes les réponses doivent désigner le même paiement. Résultats : " + outcomes);
            assertTrue(captured <= 1L, "Au plus une capture. Résultats : " + outcomes);
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    public void replayWithSameKeyAfterCapture_returnsExistingPaymentWithoutNewRow() {
        var q = acceptedQuote();
        String key = "replay-" + UUID.randomUUID();
        var first = paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals(PaymentStatus.CAPTURED, first.getStatus());

        var replay = paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);
        assertEquals(first.getId(), replay.getId());
        long rows = jdbc.queryForObject("SELECT count(*) FROM payments WHERE quote_id = ?", Long.class, q.quoteId());
        assertEquals(1L, rows);
    }

    @Test
    public void replayWithSameKeyButDifferentMethod_isIdempotencyConflict() {
        var q = acceptedQuote();
        String key = "conflict-" + UUID.randomUUID();
        paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, key);

        var ex = assertThrows(PaymentException.class,
                () -> paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CASH, key));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    public void newKeyAfterCapture_isRefusedAndQuoteStaysSinglyPaid() {
        var q = acceptedQuote();
        paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, "first-" + UUID.randomUUID());

        var ex = assertThrows(PaymentException.class,
                () -> paymentService.createForQuote(q.quoteId(), q.owner(), q.total(), PaymentMethod.CARD, "second-" + UUID.randomUUID()));
        assertEquals("QUOTE_ALREADY_PAID", ex.getCode());
        long captured = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", Long.class, q.quoteId());
        assertEquals(1L, captured);
    }
}
