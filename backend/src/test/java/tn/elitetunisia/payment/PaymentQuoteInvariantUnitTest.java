package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.payment.provider.PaymentProvider;
import tn.elitetunisia.payment.provider.PaymentResult;
import tn.elitetunisia.quote.QuoteEntity;
import tn.elitetunisia.quote.QuoteRepository;
import tn.elitetunisia.quote.QuoteStatus;

class PaymentQuoteInvariantUnitTest {

    private static final List<PaymentStatus> UNRESOLVED = List.of(
            PaymentStatus.PENDING, PaymentStatus.AUTHORIZED, PaymentStatus.UNKNOWN, PaymentStatus.MANUAL_REVIEW);

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentEventRepository events = mock(PaymentEventRepository.class);
    private final PaymentProvider provider = mock(PaymentProvider.class);
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final BookingService bookingService = mock(BookingService.class);

    private final UUID quoteId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final BigDecimal total = BigDecimal.valueOf(250);

    private QuoteEntity acceptedQuote() {
        QuoteEntity quote = mock(QuoteEntity.class);
        BookingEntity booking = mock(BookingEntity.class);
        when(quote.getId()).thenReturn(quoteId);
        when(quote.getCustomerId()).thenReturn(customerId);
        when(quote.getStatus()).thenReturn(QuoteStatus.ACCEPTED);
        when(quote.getTotalAmount()).thenReturn(total);
        when(quote.getCurrency()).thenReturn("EUR");
        when(quote.getBooking()).thenReturn(booking);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getStatus()).thenReturn(BookingStatus.PENDING);
        when(quotes.findBookingIdForCustomer(quoteId, customerId)).thenReturn(Optional.of(bookingId));
        when(bookingService.lockForCommercialOperation(bookingId)).thenReturn(booking);
        when(quotes.findLockedById(quoteId)).thenReturn(Optional.of(quote));
        return quote;
    }

    private PaymentService service() {
        return new PaymentService(payments, events, provider, quotes, bookingService,
                new org.springframework.transaction.support.TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class)));
    }

    @Test
    void rejectsNewAttemptWhenQuoteAlreadyCaptured() {
        acceptedQuote();
        when(payments.existsByQuote_IdAndStatusIn(eq(quoteId), eq(List.of(PaymentStatus.CAPTURED)))).thenReturn(true);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> service().createForQuote(quoteId, customerId, total, PaymentMethod.CARD, "key-new"));

        assertEquals("QUOTE_ALREADY_PAID", ex.getCode());
        verifyNoInteractions(provider);
        verify(payments, never()).saveAndFlush(any(PaymentEntity.class));
    }

    @Test
    void rejectsNewAttemptWhilePreviousAttemptIsPending() {
        acceptedQuote();
        when(payments.existsByQuote_IdAndStatusIn(eq(quoteId), eq(List.of(PaymentStatus.CAPTURED)))).thenReturn(false);
        when(payments.existsByQuote_IdAndStatusIn(eq(quoteId), eq(UNRESOLVED))).thenReturn(true);

        PaymentException ex = assertThrows(PaymentException.class,
                () -> service().createForQuote(quoteId, customerId, total, PaymentMethod.CARD, "key-other"));

        assertEquals("PAYMENT_IN_PROGRESS", ex.getCode());
        verifyNoInteractions(provider);
        verify(payments, never()).saveAndFlush(any(PaymentEntity.class));
    }

    // finish relit la tentative sous verrou : elle doit être celle qui vient d'être enregistrée.
    private void stubFindForUpdateReturnsSaved() {
        var saved = new java.util.concurrent.atomic.AtomicReference<PaymentEntity>();
        when(payments.saveAndFlush(any())).thenAnswer(inv -> { saved.set(inv.getArgument(0)); return saved.get(); });
        when(payments.findForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));
    }

    @Test
    void capturesWhenNoCapturedOrInProgressPaymentExists() {
        acceptedQuote();
        when(payments.existsByQuote_IdAndStatusIn(any(), anyCollection())).thenReturn(false);
        stubFindForUpdateReturnsSaved();
        when(provider.process(any(PaymentEntity.class))).thenReturn(new PaymentResult(PaymentStatus.CAPTURED, "TX-1", "ok"));

        PaymentEntity result = service().createForQuote(quoteId, customerId, total, PaymentMethod.CARD, "key-ok");

        assertEquals(PaymentStatus.CAPTURED, result.getStatus());
        assertEquals("TX-1", result.getTransactionRef());
        assertNotNull(result.getCapturedAt());
        verify(provider, times(1)).process(any(PaymentEntity.class));
    }

    @Test
    void providerErrorLeavesAttemptUnknownWithoutFailedOrException() {
        acceptedQuote();
        when(payments.existsByQuote_IdAndStatusIn(any(), anyCollection())).thenReturn(false);
        when(provider.process(any(PaymentEntity.class))).thenThrow(new IllegalStateException("timeout fournisseur"));

        PaymentEntity result = service().createForQuote(quoteId, customerId, total, PaymentMethod.CARD, "key-timeout");

        assertEquals(PaymentStatus.UNKNOWN, result.getStatus());
        assertNull(result.getCapturedAt());
        verify(events).saveAndFlush(argThatHasAction("PROVIDER_UNKNOWN"));
        verify(payments, never()).saveAndFlush(argThatHasStatus(PaymentStatus.FAILED));
    }

    @Test
    void sameKeyReturnsExistingPaymentWithoutNewAttempt() {
        QuoteEntity quote = acceptedQuote();
        PaymentEntity existing = new PaymentEntity(quote, customerId, total, "EUR", PaymentMethod.CARD, "key-same");
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(customerId, quoteId, "key-same")).thenReturn(Optional.of(existing));

        PaymentEntity result = service().createForQuote(quoteId, customerId, total, PaymentMethod.CARD, "key-same");

        assertSame(existing, result);
        verifyNoInteractions(provider);
        verify(payments, never()).existsByQuote_IdAndStatusIn(any(), anyCollection());
    }

    @Test
    void newAttemptAllowedAfterDefinitiveFailure() {
        acceptedQuote();
        when(payments.existsByQuote_IdAndStatusIn(any(), anyCollection())).thenReturn(false);
        stubFindForUpdateReturnsSaved();
        when(provider.process(any(PaymentEntity.class))).thenReturn(new PaymentResult(PaymentStatus.CAPTURED, "TX-2", "ok"));

        PaymentEntity result = service().createForQuote(quoteId, customerId, total, PaymentMethod.CARD, "key-after-failed");

        assertEquals(PaymentStatus.CAPTURED, result.getStatus());
        verify(payments).existsByQuote_IdAndStatusIn(eq(quoteId), eq(List.of(PaymentStatus.CAPTURED)));
        verify(payments).existsByQuote_IdAndStatusIn(eq(quoteId), eq(UNRESOLVED));
        verify(payments, never()).existsByQuote_IdAndStatusIn(any(), argThat(c -> c.contains(PaymentStatus.FAILED)));
    }

    private static PaymentEventEntity argThatHasAction(String action) {
        return org.mockito.ArgumentMatchers.argThat(e -> e != null && action.equals(e.getAction()));
    }

    private static PaymentEntity argThatHasStatus(PaymentStatus status) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && p.getStatus() == status);
    }
}
