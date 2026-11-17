package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.booking.BookingStatus;
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
    BookingService bookingService = mock(BookingService.class);
    BookingEntity booking;
    PaymentService svc;

    @BeforeEach
    void init() { svc = new PaymentService(payments, events, provider, quotes, bookingService, transactionTemplate()); }

    static org.springframework.transaction.support.TransactionTemplate transactionTemplate() {
        return new org.springframework.transaction.support.TransactionTemplate(
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    QuoteEntity makeQuote(UUID id, UUID customerId, BigDecimal total) {
        var q = mock(QuoteEntity.class);
        when(q.getId()).thenReturn(id);
        when(q.getCustomerId()).thenReturn(customerId);
        when(q.getStatus()).thenReturn(QuoteStatus.ACCEPTED);
        when(q.getTotalAmount()).thenReturn(total);
        when(q.getCurrency()).thenReturn("TND");
        booking = mock(BookingEntity.class);
        UUID bookingId = UUID.randomUUID();
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(booking.getStartDateTime()).thenReturn(java.time.Instant.parse("2030-01-01T10:00:00Z"));
        when(q.getBooking()).thenReturn(booking);
        when(quotes.findBookingIdForCustomer(id, customerId)).thenReturn(Optional.of(bookingId));
        when(bookingService.lockForCommercialOperation(bookingId)).thenReturn(booking);
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
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k1"))).thenReturn(Optional.of(existing));

        var ex = assertThrows(PaymentException.class, () -> svc.createForQuote(qid, cid, new BigDecimal("100.00"), PaymentMethod.CARD, "k1"));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    public void replayWithDifferentAmountIsIdempotencyConflictNotAmountMismatch() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("100.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        var existing = mock(PaymentEntity.class);
        when(existing.getMethod()).thenReturn(PaymentMethod.CARD);
        when(existing.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k-replay"))).thenReturn(Optional.of(existing));

        var ex = assertThrows(PaymentException.class, () -> svc.createForQuote(qid, cid, new BigDecimal("90.00"), PaymentMethod.CARD, "k-replay"));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
        verify(payments, never()).saveAndFlush(any());
    }

    // finish relit la tentative sous verrou : elle doit être celle qui vient d'être enregistrée.
    private void stubFindForUpdateReturnsSaved() {
        var saved = new java.util.concurrent.atomic.AtomicReference<PaymentEntity>();
        when(payments.saveAndFlush(any())).thenAnswer(inv -> { saved.set(inv.getArgument(0)); return saved.get(); });
        when(payments.findForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));
    }

    @Test
    public void successfulCaptureDoesNotAutoConfirmBooking() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k2"))).thenReturn(Optional.empty());
        stubFindForUpdateReturnsSaved();

        var p = svc.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k2");
        assertEquals(PaymentStatus.CAPTURED, p.getStatus());
        assertNotNull(p.getTransactionRef());
        // La confirmation reste une transition explicite de la réservation : le paiement ne la déclenche pas.
        verify(booking, never()).transition(any(), any());
    }

    @Test
    public void rejectsPaymentWhenBookingIsNotPending() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k3"))).thenReturn(Optional.empty());
        when(booking.getStatus()).thenReturn(BookingStatus.CANCELLED);

        var ex = assertThrows(PaymentException.class, () -> svc.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k3"));
        assertEquals("BOOKING_NOT_PAYABLE", ex.getCode());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    public void providerErrorLeavesPaymentUnknownWithoutFailing() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k5"))).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        PaymentProvider failing = mock(PaymentProvider.class);
        when(failing.process(any())).thenThrow(new RuntimeException("timeout"));
        var failingSvc = new PaymentService(payments, events, failing, quotes, bookingService, transactionTemplate());

        var p = failingSvc.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k5");
        assertEquals(PaymentStatus.UNKNOWN, p.getStatus());
        verify(events).saveAndFlush(argThat(e -> "PROVIDER_UNKNOWN".equals(e.getAction())));
        verify(failing, times(1)).process(any());
    }

    @Test
    public void sameIdempotencyKeyReturnsExistingPaymentWithoutProviderCall() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        var existing = mock(PaymentEntity.class);
        when(existing.getMethod()).thenReturn(PaymentMethod.CARD);
        when(existing.getAmount()).thenReturn(new BigDecimal("120.00"));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k6"))).thenReturn(Optional.of(existing));
        PaymentProvider provider = mock(PaymentProvider.class);
        var s = new PaymentService(payments, events, provider, quotes, bookingService, transactionTemplate());

        assertSame(existing, s.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k6"));
        verifyNoInteractions(provider);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    public void constraintViolationWithoutSameKeyPaymentIsNotMappedToIdempotencyConflict() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k7"))).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq"));
        PaymentProvider provider = mock(PaymentProvider.class);
        var s = new PaymentService(payments, events, provider, quotes, bookingService, transactionTemplate());

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> s.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k7"));
        verifyNoInteractions(provider);
    }


    @Test
    public void concurrentIdenticalRequestReplaysWinnerPaymentWithoutProviderCall() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        var winner = mock(PaymentEntity.class);
        when(winner.getMethod()).thenReturn(PaymentMethod.CARD);
        when(winner.getAmount()).thenReturn(new BigDecimal("120.00"));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k10")))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(payments.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uq"));
        PaymentProvider provider = mock(PaymentProvider.class);
        var s = new PaymentService(payments, events, provider, quotes, bookingService, transactionTemplate());

        assertSame(winner, s.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k10"));
        verifyNoInteractions(provider);
    }

    @Test
    public void pendingPaymentBlocksSecondPaymentForSameQuote() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k8"))).thenReturn(Optional.empty());
        when(payments.existsByQuote_IdAndStatusIn(eq(qid), eq(List.of(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED, PaymentStatus.UNKNOWN, PaymentStatus.MANUAL_REVIEW)))).thenReturn(true);
        PaymentProvider provider = mock(PaymentProvider.class);
        var s = new PaymentService(payments, events, provider, quotes, bookingService, transactionTemplate());

        var ex = assertThrows(PaymentException.class, () -> s.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k8"));
        assertEquals("PAYMENT_IN_PROGRESS", ex.getCode());
        verifyNoInteractions(provider);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    public void finalisationErrorIsPropagatedNotSwallowed() {
        UUID qid = UUID.randomUUID(); UUID cid = UUID.randomUUID();
        var q = makeQuote(qid, cid, new BigDecimal("120.00"));
        when(quotes.findLockedById(eq(qid))).thenReturn(Optional.of(q));
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(eq(cid), eq(qid), eq("k9"))).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        // 1er appel : réservation ; 2e appel : finalisation (échec simulé).
        when(bookingService.lockForCommercialOperation(any())).thenReturn(booking).thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class, () -> svc.createForQuote(qid, cid, new BigDecimal("120.00"), PaymentMethod.CARD, "k9"));
    }

}
