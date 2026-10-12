package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("postgres")
public class PostgresIdempotencyIT {
    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository payments;

    @Test
    public void concurrentRequestsWithSameIdempotencyKey_resultInSinglePayment() throws Exception {
        var quoteId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        // This IT is gated and requires a PostgreSQL test environment with real DB and suitable fixtures.
        // If Postgres is not available the project test run should skip @Tag("postgres") tests.

        var key = "concurrent-key-" + UUID.randomUUID();
        int threads = 8;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Callable<PaymentEntity> task = () -> {
            start.await();
            try {
                return paymentService.createForQuote(quoteId, customerId, new BigDecimal("100.00"), PaymentMethod.CARD, key);
            } catch (Exception e) {
                throw e;
            }
        };

        Future<PaymentEntity>[] futures = new Future[threads];
        for (int i = 0; i < threads; i++) futures[i] = ex.submit(task);
        start.countDown();

        PaymentEntity first = null;
        int successes = 0;
        for (int i = 0; i < threads; i++) {
            try {
                var p = futures[i].get(30, TimeUnit.SECONDS);
                if (first == null) first = p;
                else assertEquals(first.getId(), p.getId());
                successes++;
            } catch (ExecutionException ee) {
                // record failures; allowed if some threads hit conflict but DB must contain single row
            }
        }
        ex.shutdownNow();

        long count = payments.countByIdempotencyKey(key);
        assertEquals(1L, count, "There must be exactly one payment row for the idempotency key");
        assertTrue(successes >= 1, "At least one call must succeed");
    }
}
