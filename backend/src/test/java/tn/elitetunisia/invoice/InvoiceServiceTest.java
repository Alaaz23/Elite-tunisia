package tn.elitetunisia.invoice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import tn.elitetunisia.payment.PaymentEntity;
import tn.elitetunisia.payment.PaymentRepository;

class InvoiceServiceTest {
    @Mock InvoiceRepository invoices;
    @Mock InvoiceEventRepository events;
    @Mock tn.elitetunisia.quote.QuoteRepository quotes;
    @Mock tn.elitetunisia.booking.BookingRepository bookings;
    @Mock PaymentRepository payments;
    @Mock JdbcTemplate jdbc;

    InvoiceService service;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        service = new InvoiceService(invoices, events, quotes, bookings, payments, jdbc);
    }

    @Test
    void attachPayment_requiresCaptured() {
        UUID invId = UUID.randomUUID();
        UUID payId = UUID.randomUUID();
        InvoiceEntity inv = mock(InvoiceEntity.class);
        when(invoices.findById(invId)).thenReturn(Optional.of(inv));
        PaymentEntity pay = mock(PaymentEntity.class);
        when(payments.findById(payId)).thenReturn(Optional.of(pay));
        when(pay.getStatus()).thenReturn(tn.elitetunisia.payment.PaymentStatus.PENDING);

        try {
            service.attachPayment(invId, payId);
            throw new AssertionError("Expected exception");
        } catch (RuntimeException expected) {
            // expected
        }
        verify(jdbc, never()).update(any(String.class), any(), any());
    }
}
