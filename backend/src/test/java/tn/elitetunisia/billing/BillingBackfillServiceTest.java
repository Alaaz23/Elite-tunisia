package tn.elitetunisia.billing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tn.elitetunisia.common.exception.DomainException;

class BillingBackfillServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BillingRequestService billing = mock(BillingRequestService.class);
    private final BillingBackfillService backfill = new BillingBackfillService(jdbc, billing);

    /** Simule une ligne du SQL de classification. */
    private void givenCandidate(UUID id, String status, boolean ret, boolean quote, boolean data, boolean invoice, boolean dead) throws Exception {
        givenCandidate(id, status, ret, quote, data, data, data, invoice, dead);
    }

    private void givenCandidate(UUID id, String status, boolean ret, boolean quote, boolean identity, boolean vehicle,
            boolean amount, boolean invoice, boolean dead) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("reference")).thenReturn("REF-" + id.toString().substring(0, 6));
        when(rs.getString("status")).thenReturn(status);
        when(rs.getBoolean("has_return")).thenReturn(ret);
        when(rs.getBoolean("has_accepted_quote")).thenReturn(quote);
        when(rs.getBoolean("has_customer_identity")).thenReturn(identity);
        when(rs.getBoolean("has_vehicle")).thenReturn(vehicle);
        when(rs.getBoolean("has_amount")).thenReturn(amount);
        when(rs.getBoolean("has_main_invoice")).thenReturn(invoice);
        when(rs.getBoolean("has_dead_request")).thenReturn(dead);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    /**
     * Prise du verrou : la requête conditionnelle renvoie une ligne par tentative. Liste vide = verrou occupé ;
     * élément null = verrou libre ; élément non nul = reprise d'un verrou expiré.
     */
    private void givenLock(List<UUID> previousOwner) {
        doReturn(previousOwner).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    /** Heartbeat et écritures d'état : 1 ligne touchée (verrou toujours détenu). */
    private void givenLockHeldByThisRun() {
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void dryRunClassifiesCompletedBookingAsEligibleWithoutWrites() throws Exception {
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, false);

        var report = backfill.dryRun();

        assertTrue(report.dryRun());
        assertEquals(1, report.eligible().size());
        assertTrue(report.skipped().isEmpty());
        assertFalse(report.fingerprint().isBlank());
        verifyNoInteractions(billing);
    }

    @Test
    void notCompletedOrAlreadyInvoicedBookingsAreSkippedWithReason() throws Exception {
        givenCandidate(UUID.randomUUID(), "IN_PROGRESS", true, true, true, false, false);
        assertEquals("NOT_COMPLETED", backfill.dryRun().skipped().get(0).detail());

        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, true, false);
        assertEquals("ALREADY_INVOICED", backfill.dryRun().skipped().get(0).detail());
    }

    @Test
    void missingCustomerIdentityVehicleOrAmountIsSkippedWithPreciseReason() throws Exception {
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, false, true, true, false, false);
        assertEquals("MISSING_CUSTOMER_IDENTITY", backfill.dryRun().skipped().get(0).detail());

        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, true, false, false);
        assertEquals("MISSING_VEHICLE", backfill.dryRun().skipped().get(0).detail());

        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, true, false, false, false);
        assertEquals("AMOUNT_NOT_COMPUTABLE", backfill.dryRun().skipped().get(0).detail());
    }

    @Test
    void executeRefusesWithoutMatchingFingerprint() throws Exception {
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, false);

        assertThrows(DomainException.class, () -> backfill.execute(UUID.randomUUID(), null));
        assertThrows(DomainException.class, () -> backfill.execute(UUID.randomUUID(), "empreinte-obsolete"));
        verifyNoInteractions(billing);
    }

    @Test
    void executeIsRejectedWhileAnotherRunHoldsTheLock() throws Exception {
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, false);
        String fingerprint = backfill.dryRun().fingerprint();
        givenLock(List.of());

        assertThrows(DomainException.class, () -> backfill.execute(UUID.randomUUID(), fingerprint));
        verifyNoInteractions(billing);
    }

    @Test
    void executeWithConfirmedFingerprintProcessesEligibleBooking() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, false);
        String fingerprint = backfill.dryRun().fingerprint();
        givenLock(Collections.singletonList(null));
        givenLockHeldByThisRun();
        var request = mock(BillingRequestEntity.class);
        when(request.getId()).thenReturn(requestId);
        when(request.getInvoiceId()).thenReturn(UUID.randomUUID());
        when(billing.enqueueMainInvoice(any(), any())).thenReturn(request);
        when(billing.process(requestId)).thenReturn(BillingRequestService.Outcome.DONE);
        when(billing.get(requestId)).thenReturn(request);

        var report = backfill.execute(actor, fingerprint);

        assertFalse(report.dryRun());
        assertEquals(1, report.created().size());
        assertTrue(report.failed().isEmpty());
        verify(billing).enqueueMainInvoice(any(), any());
        verify(billing).process(requestId);
        verify(jdbc).update(contains("status = ?"), any(Object[].class));
    }

    @Test
    void expiredLockIsTakenOverAndPreviousRunIsRecordedAsTakenOver() throws Exception {
        UUID stale = UUID.randomUUID();
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, false);
        String fingerprint = backfill.dryRun().fingerprint();
        givenLock(List.of(stale));
        givenLockHeldByThisRun();

        backfill.execute(UUID.randomUUID(), fingerprint);

        verify(jdbc).update(contains("set status = 'TAKEN_OVER'"), any(Object[].class));
    }

    @Test
    void lostLockAbortsBeforeAnyInvoiceIsEnqueued() throws Exception {
        givenCandidate(UUID.randomUUID(), "COMPLETED", true, true, true, false, false);
        String fingerprint = backfill.dryRun().fingerprint();
        givenLock(Collections.singletonList(null));
        doReturn(0).when(jdbc).update(contains("set heartbeat_at = ? where id = 1 and run_id = ?"), any(Object[].class));

        assertThrows(DomainException.class, () -> backfill.execute(UUID.randomUUID(), fingerprint));
        verifyNoInteractions(billing);
        verify(jdbc).update(contains("status = ?"), any(Object[].class));
    }
}
