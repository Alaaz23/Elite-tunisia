package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

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
public class PaymentQuoteCapturePostgresIT {
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

    @Test
    public void concurrentDifferentKeysOnSameQuote_produceExactlyOneCapture() throws Exception {
        var fixture = fixtures.createBooking(24);
        var request = new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false,
                java.math.BigDecimal.ZERO, "", "", List.of());
        var quote = quotes.create(fixture.booking().id(), request, fixture.owner(), null);
        quotes.send(quote.id(), fixture.owner(), null);
        quotes.customerAction(quote.id(), fixture.owner(), "accept", null, null);

        int threads = 6;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String key = "distinct-key-" + UUID.randomUUID();
                futures.add(ex.submit(() -> {
                    start.await();
                    try {
                        return paymentService.createForQuote(quote.id(), fixture.owner(), quote.totalAmount(),
                                PaymentMethod.CARD, key).getStatus().name();
                    } catch (PaymentException e) {
                        return e.getCode();
                    }
                }));
            }
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }

            long captured = jdbc.queryForObject(
                    "SELECT count(*) FROM payments WHERE quote_id = ? AND status = 'CAPTURED'", Long.class, quote.id());
            assertEquals(1L, captured, "Un seul paiement capturé par devis. Résultats : " + outcomes);
            long rejected = outcomes.stream()
                    .filter(o -> o.equals("QUOTE_ALREADY_PAID") || o.equals("PAYMENT_IN_PROGRESS")).count();
            assertEquals(threads - 1L, rejected, "Les autres tentatives doivent être refusées. Résultats : " + outcomes);
        } finally {
            ex.shutdownNow();
        }
    }
}
