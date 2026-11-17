package tn.elitetunisia.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock tn.elitetunisia.booking.BookingService bookingService;
    @Mock jakarta.persistence.EntityManager entityManager;

    InvoiceService service;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        service = new InvoiceService(invoices, events, quotes, bookings, payments, jdbc, bookingService, entityManager);
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

    @Test
    void createFromQuote_sequenceFailureIsPropagatedWithoutTimeBasedFallback() {
        UUID quoteId = UUID.randomUUID();
        var quote = acceptedQuoteMock(quoteId);
        when(invoices.findFirstByBookingIdAndQuoteIsNotNull(any())).thenReturn(Optional.empty());
        when(jdbc.queryForObject("select nextval('invoice_seq')", Long.class))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sequence unavailable"));

        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.createFromQuote(quoteId, UUID.randomUUID(), 7));
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void createFromQuote_returnsExistingMainInvoiceWithoutCreatingAnother() {
        UUID quoteId = UUID.randomUUID();
        acceptedQuoteMock(quoteId);
        var existing = mock(InvoiceEntity.class);
        when(invoices.findFirstByBookingIdAndQuoteIsNotNull(any())).thenReturn(Optional.of(existing));

        assertSame(existing, service.createFromQuote(quoteId, UUID.randomUUID(), 14));
        verify(jdbc, never()).queryForObject("select nextval('invoice_seq')", Long.class);
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void createFromQuote_locksBookingBeforeQuoteAndRejectsNonAcceptedQuote() {
        UUID quoteId = UUID.randomUUID();
        var quote = acceptedQuoteMock(quoteId);
        when(quote.getStatus()).thenReturn(tn.elitetunisia.quote.QuoteStatus.SENT);

        assertThrows(tn.elitetunisia.common.exception.DomainException.class,
                () -> service.createFromQuote(quoteId, UUID.randomUUID(), 7));

        var order = inOrder(bookingService, quotes);
        order.verify(bookingService).lockForOperation(any());
        order.verify(quotes).findLockedById(quoteId);
        verify(invoices, never()).findFirstByBookingIdAndQuoteIsNotNull(any());
    }

    @Test
    void mainInvoiceAfterReturnRefusesBookingNotCompleted() {
        var booking = mock(tn.elitetunisia.booking.BookingEntity.class);
        when(booking.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.IN_PROGRESS);

        assertThrows(IllegalStateException.class, () -> service.createMainInvoiceAfterReturn(booking, UUID.randomUUID()));
        verify(quotes, never()).findFirstByBookingIdAndStatusOrderByVersionDesc(any(), any());
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void mainInvoiceAfterReturnFailsWhenNoAcceptedQuote() {
        var booking = mock(tn.elitetunisia.booking.BookingEntity.class);
        when(booking.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.COMPLETED);
        when(quotes.findFirstByBookingIdAndStatusOrderByVersionDesc(any(), any())).thenReturn(Optional.empty());

        var ex = assertThrows(tn.elitetunisia.common.exception.DomainException.class,
                () -> service.createMainInvoiceAfterReturn(booking, UUID.randomUUID()));
        assertEquals("QUOTE_NOT_ACCEPTED", ex.getCode());
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test
    void mainInvoiceAfterReturnIsIdempotentPerBooking() {
        UUID quoteId = UUID.randomUUID();
        var quote = acceptedQuoteMock(quoteId);
        var booking = mock(tn.elitetunisia.booking.BookingEntity.class);
        when(booking.getStatus()).thenReturn(tn.elitetunisia.booking.BookingStatus.COMPLETED);
        when(quotes.findFirstByBookingIdAndStatusOrderByVersionDesc(any(), any())).thenReturn(Optional.of(quote));
        var existing = mock(InvoiceEntity.class);
        when(invoices.findFirstByBookingIdAndQuoteIsNotNull(any())).thenReturn(Optional.of(existing));

        assertSame(existing, service.createMainInvoiceAfterReturn(booking, UUID.randomUUID()));
        verify(jdbc, never()).queryForObject("select nextval('invoice_seq')", Long.class);
        verify(invoices, never()).saveAndFlush(any());
    }

    private tn.elitetunisia.quote.QuoteEntity acceptedQuoteMock(UUID quoteId) {
        var quote = mock(tn.elitetunisia.quote.QuoteEntity.class);
        var booking = mock(tn.elitetunisia.booking.BookingEntity.class);
        when(booking.getId()).thenReturn(UUID.randomUUID());
        when(quote.getBooking()).thenReturn(booking);
        when(quote.getId()).thenReturn(quoteId);
        when(quote.getStatus()).thenReturn(tn.elitetunisia.quote.QuoteStatus.ACCEPTED);
        when(quotes.findById(quoteId)).thenReturn(Optional.of(quote));
        when(quotes.findLockedById(quoteId)).thenReturn(Optional.of(quote));
        return quote;
    }

    @Test
    void attachPayment_doesNotInsertWhenLinkAlreadyExists() {
        UUID invId = UUID.randomUUID();
        UUID payId = UUID.randomUUID();
        var inv = mock(InvoiceEntity.class);
        var pay = mock(PaymentEntity.class);
        when(inv.getId()).thenReturn(invId);
        when(pay.getId()).thenReturn(payId);
        when(pay.getStatus()).thenReturn(tn.elitetunisia.payment.PaymentStatus.CAPTURED);
        when(invoices.findById(invId)).thenReturn(Optional.of(inv));
        when(payments.findById(payId)).thenReturn(Optional.of(pay));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), any(), any())).thenReturn(1);

        service.attachPayment(invId, payId);

        verify(jdbc, never()).update(eq("insert into invoice_payments(invoice_id,payment_id) values(?,?)"), any(), any());
    }

    @Test
    void attachPayment_propagatesInsertErrorInsteadOfSwallowingIt() {
        UUID invId = UUID.randomUUID();
        UUID payId = UUID.randomUUID();
        var inv = mock(InvoiceEntity.class);
        var pay = mock(PaymentEntity.class);
        when(inv.getId()).thenReturn(invId);
        when(pay.getId()).thenReturn(payId);
        when(pay.getStatus()).thenReturn(tn.elitetunisia.payment.PaymentStatus.CAPTURED);
        when(invoices.findById(invId)).thenReturn(Optional.of(inv));
        when(payments.findById(payId)).thenReturn(Optional.of(pay));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(invId), eq(payId))).thenReturn(0);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("pk violation"))
                .when(jdbc).update("insert into invoice_payments(invoice_id,payment_id) values(?,?)", invId, payId);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.attachPayment(invId, payId));
        verify(events, never()).saveAndFlush(any());
    }
}
