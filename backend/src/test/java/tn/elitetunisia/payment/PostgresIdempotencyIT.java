package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
public class PostgresIdempotencyIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository payments;
    @Autowired TestFixtures fixtures;
    @Autowired QuoteService quotes;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    @Test
    public void concurrentRequestsWithSameIdempotencyKey_resultInSinglePayment() throws Exception {
        var fixture = fixtures.createBooking(24);
        var request = new QuoteRequest(Instant.now().plusSeconds(10 * 86400L).truncatedTo(java.time.temporal.ChronoUnit.MICROS), false,
                java.math.BigDecimal.ZERO, "", "", List.of());
        var quote = quotes.create(fixture.booking().id(), request, fixture.owner(), null);
        quotes.send(quote.id(), fixture.owner(), null);
        quotes.customerAction(quote.id(), fixture.owner(), "accept", null, null);
        var key = "concurrent-key-" + UUID.randomUUID();
        int threads = 8;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(ex.submit(() -> {
                    start.await();
                    return paymentService.createForQuote(quote.id(), fixture.owner(), quote.totalAmount(),
                            PaymentMethod.CARD, key).getId();
                }));
            }
            start.countDown();

            UUID paymentId = futures.get(0).get(30, TimeUnit.SECONDS);
            for (Future<UUID> future : futures) {
                assertEquals(paymentId, future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1L, payments.countByIdempotencyKey(key),
                    "There must be exactly one payment row for the idempotency key");
        } finally {
            ex.shutdownNow();
        }
    }
}
