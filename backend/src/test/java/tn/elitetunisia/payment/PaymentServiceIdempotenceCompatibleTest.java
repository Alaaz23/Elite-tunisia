package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import tn.elitetunisia.quote.QuoteEntity;
import tn.elitetunisia.payment.provider.PaymentProvider;

class PaymentServiceIdempotenceCompatibleTest {

    @Test
    void returnsExistingWhenIdempotencyKeyFound() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentEventRepository events = mock(PaymentEventRepository.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        tn.elitetunisia.quote.QuoteRepository quotes = mock(tn.elitetunisia.quote.QuoteRepository.class);

        UUID quoteId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idem = "idem-1";

        QuoteEntity quote = mock(QuoteEntity.class);
        when(quote.getId()).thenReturn(quoteId);
        when(quote.getCustomerId()).thenReturn(customerId);
        when(quote.getStatus()).thenReturn(tn.elitetunisia.quote.QuoteStatus.ACCEPTED);
        when(quote.getTotalAmount()).thenReturn(BigDecimal.valueOf(200));
        when(quote.getCurrency()).thenReturn("USD");

        when(quotes.findLockedById(quoteId)).thenReturn(Optional.of(quote));
        when(quotes.findBookingIdForCustomer(quoteId, customerId)).thenReturn(Optional.of(UUID.randomUUID()));

        PaymentEntity existing = new PaymentEntity(quote, customerId, BigDecimal.valueOf(200), "USD", PaymentMethod.CARD, idem);
        when(payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(customerId, quoteId, idem)).thenReturn(Optional.of(existing));

        PaymentService svc = new PaymentService(payments, events, provider, quotes, mock(tn.elitetunisia.booking.BookingService.class),
                new org.springframework.transaction.support.TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class)));

        PaymentEntity result = svc.createForQuote(quoteId, customerId, BigDecimal.valueOf(200), PaymentMethod.CARD, idem);

        assertSame(existing, result);
    }

    @Test
    void rejectsConcurrentInsertWithIdempotencyConflict() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentEventRepository events = mock(PaymentEventRepository.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        tn.elitetunisia.quote.QuoteRepository quotes = mock(tn.elitetunisia.quote.QuoteRepository.class);

        UUID quoteId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idem = "idem-2";

        QuoteEntity quote = mock(QuoteEntity.class);
        when(quote.getId()).thenReturn(quoteId);
        when(quote.getCustomerId()).thenReturn(customerId);
        when(quote.getStatus()).thenReturn(tn.elitetunisia.quote.QuoteStatus.ACCEPTED);
        when(quote.getTotalAmount()).thenReturn(BigDecimal.valueOf(300));
        when(quote.getCurrency()).thenReturn("EUR");

        when(quotes.findLockedById(quoteId)).thenReturn(Optional.of(quote));
        UUID bookingId = UUID.randomUUID();
        when(quotes.findBookingIdForCustomer(quoteId, customerId)).thenReturn(Optional.of(bookingId));

        tn.elitetunisia.booking.BookingEntity booking = mock(tn.elitetunisia.booking.BookingEntity.class);
        tn.elitetunisia.booking.BookingService bookingService = mock(tn.elitetunisia.booking.BookingService.class);
        when(quote.getBooking()).thenReturn(booking);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.PENDING);
        when(bookingService.lockForCommercialOperation(bookingId)).thenReturn(booking);

        // Collision de clé après le verrou : aucune clé relue ne correspond, l'erreur d'origine est relancée (pas de conflit d'idempotence).
        doThrow(new DataIntegrityViolationException("duplicate key")).when(payments).saveAndFlush(any(PaymentEntity.class));

        PaymentService svc = new PaymentService(payments, events, provider, quotes, bookingService,
                new org.springframework.transaction.support.TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class)));

        assertThrows(DataIntegrityViolationException.class,
                () -> svc.createForQuote(quoteId, customerId, BigDecimal.valueOf(300), PaymentMethod.CASH, idem));
        verifyNoInteractions(provider);
    }
}
