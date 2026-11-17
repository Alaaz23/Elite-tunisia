package tn.elitetunisia.quote;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingService;

class QuoteServiceLockTest {

    @Test
    void lockLocksBookingBeforeQuoteRowAndRefreshesWithPessimisticWrite() throws Exception {
        var quotes = mock(QuoteRepository.class);
        var bookingService = mock(BookingService.class);
        var entityManager = mock(EntityManager.class);
        var service = newService(quotes, bookingService, entityManager);

        UUID quoteId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        var booking = mock(BookingEntity.class);
        when(booking.getId()).thenReturn(bookingId);
        var observed = mock(QuoteEntity.class);
        when(observed.getId()).thenReturn(quoteId);
        when(observed.getBooking()).thenReturn(booking);
        var locked = mock(QuoteEntity.class);
        when(quotes.findById(quoteId)).thenReturn(Optional.of(observed));
        when(quotes.findLockedById(quoteId)).thenReturn(Optional.of(locked));

        assertSame(locked, invokeLock(service, quoteId, null));

        InOrder order = inOrder(bookingService, quotes, entityManager);
        order.verify(bookingService).lockForCommercialOperation(bookingId);
        order.verify(quotes).findLockedById(quoteId);
        order.verify(quotes).flush();
        order.verify(entityManager).refresh(locked, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void lockForCustomerUsesOwnerScopedLookupAndSameOrder() throws Exception {
        var quotes = mock(QuoteRepository.class);
        var bookingService = mock(BookingService.class);
        var entityManager = mock(EntityManager.class);
        var service = newService(quotes, bookingService, entityManager);

        UUID quoteId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        var booking = mock(BookingEntity.class);
        when(booking.getId()).thenReturn(bookingId);
        var observed = mock(QuoteEntity.class);
        when(observed.getId()).thenReturn(quoteId);
        when(observed.getBooking()).thenReturn(booking);
        when(quotes.findByIdAndCustomerId(quoteId, owner)).thenReturn(Optional.of(observed));
        when(quotes.findLockedById(quoteId)).thenReturn(Optional.of(mock(QuoteEntity.class)));

        invokeLock(service, quoteId, owner);

        InOrder order = inOrder(bookingService, quotes);
        order.verify(bookingService).lockForCommercialOperation(bookingId);
        order.verify(quotes).findLockedById(quoteId);
    }

    private static QuoteService newService(QuoteRepository quotes, BookingService bookingService, EntityManager entityManager) throws Exception {
        var service = new QuoteService(quotes, mock(QuoteEventRepository.class), mock(ConfiguredServiceRepository.class),
                bookingService, mock(tn.elitetunisia.booking.BookingRepository.class), mock(tn.elitetunisia.common.pricing.RentalPricingService.class),
                mock(QuoteTaxPolicy.class), mock(tn.elitetunisia.common.audit.AuditService.class), mock(org.springframework.jdbc.core.JdbcTemplate.class));
        var field = QuoteService.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(service, entityManager);
        return service;
    }

    private static QuoteEntity invokeLock(QuoteService service, UUID id, UUID owner) throws Exception {
        Method method = QuoteService.class.getDeclaredMethod("lock", UUID.class, UUID.class);
        method.setAccessible(true);
        return (QuoteEntity) method.invoke(service, id, owner);
    }
}
