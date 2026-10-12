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

        PaymentEntity existing = new PaymentEntity(quote, customerId, BigDecimal.valueOf(200), "USD", PaymentMethod.CARD, idem);
        when(payments.findByIdempotencyKey(idem)).thenReturn(Optional.of(existing));

        PaymentService svc = new PaymentService(payments, events, provider, quotes);

        PaymentEntity result = svc.createForQuote(quoteId, customerId, BigDecimal.valueOf(200), PaymentMethod.CARD, idem);

        assertSame(existing, result);
    }

    @Test
    void handlesConcurrentInsertByReadingExisting() {
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

        // simulate saveAndFlush throwing then findByIdempotencyKey returning existing
        doThrow(new DataIntegrityViolationException("duplicate key")).when(payments).saveAndFlush(any(PaymentEntity.class));
        PaymentEntity existing = new PaymentEntity(quote, customerId, BigDecimal.valueOf(300), "EUR", PaymentMethod.CASH, idem);
        when(payments.findByIdempotencyKey(idem)).thenReturn(Optional.of(existing));

        PaymentService svc = new PaymentService(payments, events, provider, quotes);

        PaymentEntity result = svc.createForQuote(quoteId, customerId, BigDecimal.valueOf(300), PaymentMethod.CASH, idem);

        assertSame(existing, result);
    }
}
