package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingException;
import tn.elitetunisia.booking.BookingRepository;
import tn.elitetunisia.booking.BookingRequest;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.invoice.InvoiceEntity;
import tn.elitetunisia.invoice.InvoiceService;

/**
 * Invariants des migrations V21 à V32 vérifiés sur PostgreSQL réel (ELITE_POSTGRES_TEST).
 * Chaque test porte sur une règle métier ou une contrainte de schéma, pas sur l'implémentation.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ELITE_POSTGRES_TEST", matches = "true")
public class MigrationInvariantsPostgresIT {
    static {
        PostgresTestHelper.configurePostgresIfRequested();
    }

    @Autowired BookingService bookingService;
    @Autowired BookingRepository bookings;
    @Autowired InvoiceService invoiceService;
    @Autowired TestFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void requirePostgres() throws Exception {
        PostgresTestHelper.requirePostgresIfRequested(jdbc);
    }

    // ---- V32 : PENDING non bloquant, sans expiration automatique ----

    @Test
    void stalePendingIsNotExpiredAndCanBeConfirmedWhenNoConflict() {
        var fixture = fixtures.createBooking(24);
        UUID id = fixture.booking().id();
        jdbc.update("update bookings set created_at = now() - interval '90 days', "
                + "hold_expires_at = now() - interval '60 days' where id = ?", id);

        fixtures.payInFull(id, fixture.owner());
        bookingService.transition(id, BookingStatus.CONFIRMED, fixture.owner(), null);

        assertEquals("CONFIRMED", statusOf(id), "un PENDING ancien n'expire pas et reste confirmable sans conflit");
    }

    @Test
    void stalePendingDoesNotBlockNewConfirmedBookingButCannotBeConfirmedAfterwards() {
        var first = fixtures.createBooking(24);
        BookingEntity firstEntity = bookings.findById(first.booking().id()).orElseThrow();
        var stale = bookingService.create(new BookingRequest(first.vehicle(),
                firstEntity.getStartDateTime(), firstEntity.getEndDateTime(), "Tunis", "Tunis", null),
                first.owner(), null);
        jdbc.update("update bookings set created_at = now() - interval '90 days' where id = ?", stale.id());
        // Le devis est créé tant que le véhicule est libre ; la contrainte porte sur la confirmation.
        fixtures.payInFull(stale.id(), first.owner());

        fixtures.payInFull(first.booking().id(), first.owner());
        bookingService.transition(first.booking().id(), BookingStatus.CONFIRMED, first.owner(), null);

        // La réservation PENDING ancienne ne bloque pas la confirmation de la réservation concurrente.
        // En revanche, sa confirmation ultérieure doit respecter la contrainte de disponibilité confirmée.
        BookingException refused = assertThrows(BookingException.class,
                () -> bookingService.transition(stale.id(), BookingStatus.CONFIRMED, first.owner(), null));
        assertEquals("VEHICLE_NOT_AVAILABLE", refused.getCode());
        assertEquals("PENDING", statusOf(stale.id()), "le PENDING refusé reste PENDING, sans transition partielle");
    }

    // ---- V21/V22 : paiements ----

    @Test
    void paymentForeignKeyToQuoteIsValidatedAndRestrictsDeletion() {
        var fixture = fixtures.createBooking(24);
        fixtures.payInFull(fixture.booking().id(), fixture.owner());
        UUID quoteId = jdbc.queryForObject("select id from quotes where booking_id = ?", UUID.class, fixture.booking().id());

        Boolean validated = jdbc.queryForObject(
                "select convalidated from pg_constraint where conname = 'fk_payments_quote'", Boolean.class);
        assertTrue(validated, "fk_payments_quote doit être VALIDATED (V22 l'a ajoutée en NOT VALID)");

        String deleteAction = jdbc.queryForObject(
                "select confdeltype::text from pg_constraint where conname = 'fk_payments_quote'", String.class);
        assertEquals("r", deleteAction, "fk_payments_quote doit être ON DELETE RESTRICT (V22)");

        // La suppression est refusée (par quote_events ou par fk_payments_quote selon l'ordre) : le devis est protégé.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from quotes where id = ?", quoteId));
    }

    @Test
    void paymentCustomerIdIsNotNullAndIdempotencyIsScopedByCustomerAndQuote() {
        var fixture = fixtures.createBooking(24);
        fixtures.payInFull(fixture.booking().id(), fixture.owner());
        UUID quoteId = jdbc.queryForObject("select id from quotes where booking_id = ?", UUID.class, fixture.booking().id());

        String nullable = jdbc.queryForObject(
                "select is_nullable from information_schema.columns where table_name = 'payments' and column_name = 'customer_id'",
                String.class);
        assertEquals("NO", nullable, "payments.customer_id doit rester NOT NULL");
        assertThrows(DataIntegrityViolationException.class, () -> insertPayment(quoteId, null, "null-customer", "PENDING"));

        String key = "same-key-" + UUID.randomUUID();
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        insertPayment(quoteId, customerA, key, "PENDING");
        insertPayment(quoteId, customerB, key, "PENDING");
        assertThrows(DataIntegrityViolationException.class, () -> insertPayment(quoteId, customerA, key, "PENDING"),
                "même client, même devis, même clé : doublon refusé");
    }

    // ---- V28/V29 : factures ----

    @Test
    void repeatedInvoiceCreationFromQuoteReturnsSingleMainInvoice() {
        var fixture = fixtures.createBooking(24);
        fixtures.payInFull(fixture.booking().id(), fixture.owner());
        UUID quoteId = jdbc.queryForObject("select id from quotes where booking_id = ?", UUID.class, fixture.booking().id());
        InvoiceEntity first = invoiceService.createFromQuote(quoteId, fixture.owner(), null);
        InvoiceEntity second = invoiceService.createFromQuote(quoteId, fixture.owner(), null);

        assertEquals(first.getId(), second.getId(), "une seule facture principale par réservation");
        Integer count = jdbc.queryForObject(
                "select count(*) from invoices where booking_id = ? and quote_id is not null", Integer.class, fixture.booking().id());
        assertEquals(1, count);
        assertEquals(0, first.getTotalAmount().compareTo(
                jdbc.queryForObject("select total_amount from quotes where id = ?", BigDecimal.class, quoteId)),
                "le montant facturé doit égaler le total du devis au millime");
    }

    @Test
    void mainInvoiceIsRefusedBeforeReturnAndLeavesNoRow() {
        var fixture = fixtures.createBooking(24);
        fixtures.payInFull(fixture.booking().id(), fixture.owner());
        bookingService.transition(fixture.booking().id(), BookingStatus.CONFIRMED, fixture.owner(), null);
        BookingEntity booking = bookings.findById(fixture.booking().id()).orElseThrow();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThrows(IllegalStateException.class,
                    () -> invoiceService.createMainInvoiceAfterReturn(booking, fixture.owner()));
            // L'exception a déjà marqué la transaction partagée en rollback-only : on annule explicitement.
            status.setRollbackOnly();
        });

        Integer count = jdbc.queryForObject(
                "select count(*) from invoices where booking_id = ?", Integer.class, fixture.booking().id());
        assertEquals(0, count, "aucune facture avant le retour du véhicule");
    }

    // ---- helpers ----

    private String statusOf(UUID bookingId) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, bookingId);
    }

    private void insertPayment(UUID quoteId, UUID customerId, String key, String status) {
        jdbc.update("insert into payments (id, quote_id, customer_id, amount, currency, method, status, idempotency_key) "
                + "values (?, ?, ?, 1.000, 'TND', 'CARD', ?, ?)",
                UUID.randomUUID(), quoteId, customerId, status, key);
    }
}
