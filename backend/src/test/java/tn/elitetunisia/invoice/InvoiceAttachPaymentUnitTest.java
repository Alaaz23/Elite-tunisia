package tn.elitetunisia.invoice;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tn.elitetunisia.payment.PaymentEntity;
import tn.elitetunisia.payment.PaymentRepository;

public class InvoiceAttachPaymentUnitTest {

    @Mock PaymentRepository payments;
    @Mock InvoiceRepository invoices;
    @Mock InvoiceEventRepository events;
    @Mock tn.elitetunisia.quote.QuoteRepository quotes;
    @Mock tn.elitetunisia.booking.BookingRepository bookings;
    @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;

    InvoiceService svc;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        svc = new InvoiceService(invoices, events, quotes, bookings, payments, jdbc);
    }

    @Test
    void attachPaymentRejectsNonCaptured() {
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentEntity payment = mock(PaymentEntity.class);
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getStatus()).thenReturn(tn.elitetunisia.payment.PaymentStatus.PENDING);
        when(invoices.findById(invoiceId)).thenReturn(Optional.of(mock(InvoiceEntity.class)));
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.attachPayment(invoiceId, paymentId));
        assertTrue(ex.getMessage().contains("PAYMENT_NOT_CAPTURED"));
    }

    @Test
    void attachPaymentAcceptsCaptured() {
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentEntity payment = mock(PaymentEntity.class);
        when(payment.getId()).thenReturn(paymentId);
        when(payment.getStatus()).thenReturn(tn.elitetunisia.payment.PaymentStatus.CAPTURED);
        when(invoices.findById(invoiceId)).thenReturn(Optional.of(mock(InvoiceEntity.class)));
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment));
        when(jdbc.update(anyString(), any(), any())).thenReturn(1);
        assertDoesNotThrow(() -> svc.attachPayment(invoiceId, paymentId));
    }
}
