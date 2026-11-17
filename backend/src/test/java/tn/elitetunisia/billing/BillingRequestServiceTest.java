package tn.elitetunisia.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.common.exception.DomainException;
import tn.elitetunisia.invoice.InvoiceEntity;
import tn.elitetunisia.invoice.InvoiceService;

class BillingRequestServiceTest {
    private final BillingRequestRepository requests = mock(BillingRequestRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final BillingRequestHistoryRepository historyRows = mock(BillingRequestHistoryRepository.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);

    private BillingRequestService service(int maxAttempts) {
        return new BillingRequestService(requests, historyRows, bookingService, invoices, tx, maxAttempts);
    }

    private BillingRequestEntity pending(UUID bookingId) {
        return new BillingRequestEntity(bookingId, BillingRequestEntity.KIND_MAIN_INVOICE, UUID.randomUUID(), Instant.now());
    }

    @Test
    void backoffScheduleIsOneFiveFifteenSixtyMinutesAndDeadAfterFifthFailure() {
        var request = pending(UUID.randomUUID());
        Instant t = Instant.parse("2030-01-01T00:00:00Z");
        for (int i = 0; i < 4; i++) request.recordFailure("boom", t, 5);
        assertEquals(BillingRequestEntity.Status.PENDING, request.getStatus());
        assertEquals(t.plus(Duration.ofHours(1)), request.getNextAttemptAt());

        request.recordFailure("boom", t, 5);
        assertEquals(BillingRequestEntity.Status.DEAD, request.getStatus());
        assertEquals(5, request.getAttempts());
    }

    @Test
    void notYetDueRequestIsNotProcessed() {
        var request = pending(UUID.randomUUID());
        request.recordFailure("boom", Instant.now(), 5);
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        assertEquals(BillingRequestService.Outcome.SKIPPED, service(5).process(request.getId()));
        verifyNoInteractions(invoices, bookingService);
    }

    @Test
    void manualRetryRequiresReasonAndOnlyWorksOnDeadRequests() {
        var request = pending(UUID.randomUUID());
        assertThrows(DomainException.class, () -> service(5).retryDeadRequest(request.getId(), UUID.randomUUID(), " "));

        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));
        assertThrows(DomainException.class, () -> service(5).retryDeadRequest(request.getId(), UUID.randomUUID(), "motif"));
    }

    @Test
    void manualRetryResetsCycleAndKeepsAttemptHistory() {
        var request = pending(UUID.randomUUID());
        Instant t = Instant.now();
        for (int i = 0; i < 5; i++) request.recordFailure("boom", t, 5);
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));

        service(5).retryDeadRequest(request.getId(), UUID.randomUUID(), "Erreur réseau corrigée");

        assertEquals(BillingRequestEntity.Status.PENDING, request.getStatus());
        assertEquals(0, request.getCycleAttempts());
        assertEquals(5, request.getAttempts());
    }

    @Test
    void successfulGenerationMarksRequestDoneWithInvoice() {
        UUID bookingId = UUID.randomUUID();
        var request = pending(bookingId);
        var invoice = mock(InvoiceEntity.class);
        UUID invoiceId = UUID.randomUUID();
        when(invoice.getId()).thenReturn(invoiceId);
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));
        when(bookingService.lockForOperation(bookingId)).thenReturn(mock(BookingEntity.class));
        when(invoices.createMainInvoiceAfterReturn(any(), any())).thenReturn(invoice);

        assertEquals(BillingRequestService.Outcome.DONE, service(5).process(request.getId()));

        assertEquals(BillingRequestEntity.Status.DONE, request.getStatus());
        assertEquals(invoiceId, request.getInvoiceId());
    }

    @Test
    void failedGenerationRecordsAttemptAndKeepsRequestRetryable() {
        UUID bookingId = UUID.randomUUID();
        var request = pending(bookingId);
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));
        when(bookingService.lockForOperation(bookingId)).thenReturn(mock(BookingEntity.class));
        when(invoices.createMainInvoiceAfterReturn(any(), any())).thenThrow(new IllegalStateException("no quote"));

        assertEquals(BillingRequestService.Outcome.RETRY, service(5).process(request.getId()));

        assertEquals(BillingRequestEntity.Status.PENDING, request.getStatus());
        assertEquals(1, request.getAttempts());
        assertTrue(request.getLastError().contains("no quote"));
        assertNull(request.getInvoiceId());
    }

    @Test
    void reachingMaxAttemptsMarksRequestDead() {
        UUID bookingId = UUID.randomUUID();
        var request = pending(bookingId);
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(requests.findLockedById(request.getId())).thenReturn(Optional.of(request));
        when(bookingService.lockForOperation(bookingId)).thenReturn(mock(BookingEntity.class));
        when(invoices.createMainInvoiceAfterReturn(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertEquals(BillingRequestService.Outcome.DEAD, service(1).process(request.getId()));
        assertEquals(BillingRequestEntity.Status.DEAD, request.getStatus());
    }

    @Test
    void alreadyFinishedRequestIsNeverRegenerated() {
        var request = pending(UUID.randomUUID());
        request.markDone(UUID.randomUUID(), Instant.now());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        assertEquals(BillingRequestService.Outcome.SKIPPED, service(5).process(request.getId()));
        verifyNoInteractions(invoices, bookingService);
    }

    @Test
    void enqueueReturnsExistingRequestWithoutInsertingSecondOne() {
        UUID bookingId = UUID.randomUUID();
        var existing = pending(bookingId);
        when(requests.findByBookingIdAndKind(bookingId, BillingRequestEntity.KIND_MAIN_INVOICE)).thenReturn(Optional.of(existing));

        assertSame(existing, service(5).enqueueMainInvoice(bookingId, UUID.randomUUID()));
        verify(requests, never()).saveAndFlush(any());
    }
}
