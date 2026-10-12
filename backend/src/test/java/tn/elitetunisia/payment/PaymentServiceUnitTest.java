package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.elitetunisia.payment.provider.PaymentProvider;
import tn.elitetunisia.payment.provider.PaymentResult;
import tn.elitetunisia.payment.provider.SimulatedPaymentProvider;
import tn.elitetunisia.quote.QuoteEntity;
import tn.elitetunisia.quote.QuoteRepository;
import tn.elitetunisia.quote.QuoteStatus;

public class PaymentServiceUnitTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentEventRepository events = mock(PaymentEventRepository.class);
    PaymentProvider provider = new SimulatedPaymentProvider();
    QuoteRepository quotes = mock(QuoteRepository.class);
    PaymentService svc;

    @BeforeEach
    void init() { svc = new PaymentService(payments, events, provider, quotes); }

    QuoteEntity makeQuote(UUID id, UUID customerId, BigDecimal total) {
        var q = mock(QuoteEntity.class);
        when(q.getId()).thenReturn(id);
        when(q.getCustomerId()).thenReturn(customerId);
        when(q.getStatus()).thenReturn(QuoteStatus.ACCEPTED);
        when(q.getTotalAmount()).thenReturn(total);
        when(q.getCurrency()).thenReturn("TND");
        when(q.getBooking()).thenReturn(null);
        return q;
    }

    @Test
    public void rejectsAmountMismatch() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("100.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));

        var ex = assertThrows(PaymentException.class, () -> svc.createForQuote(qid, cid, new BigDecimal("90.00"), PaymentMethod.CARD, "key1"));
        assertEquals("PAYMENT_AMOUNT_MISMATCH", ex.getCode());
    }

    @Test
    public void idempotencyConflictDetected() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("100.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        var existing = mock(PaymentEntity.class);
        when(existing.getQuote()).thenReturn(q);
        when(existing.getCustomerId()).thenReturn(cid);
        when(existing.getMethod()).thenReturn(PaymentMethod.CARD);
        when(existing.getAmount()).thenReturn(new BigDecimal("99.99"));
        when(payments.findByIdempotencyKey(eq("k1"))).thenReturn(Optional.of(existing));

        var ex = assertThrows(PaymentException.class, () -> svc.createForQuote(qid, cid, new BigDecimal("100.00"), PaymentMethod.CARD, "k1"));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    public void successfulCaptureDoesNotAutoConfirmBooking() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByIdempotencyKey(eq("k2"))).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var p = svc.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k2");
        assertEquals(PaymentStatus.CAPTURED, p.getStatus());
        assertNotNull(p.getTransactionRef());
        // booking confirmation is handled elsewhere; ensure quote/booking not modified here
        assertNull(p.getBooking());
    }
}
