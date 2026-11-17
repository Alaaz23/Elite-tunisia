package tn.elitetunisia.payment;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tn.elitetunisia.booking.BookingService;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.payment.provider.PaymentProvider;
import tn.elitetunisia.payment.provider.PaymentResult;
import tn.elitetunisia.quote.QuoteEntity;
import tn.elitetunisia.quote.QuoteRepository;
import tn.elitetunisia.quote.QuoteStatus;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final PaymentProvider provider;
    private final QuoteRepository quotes;
    private final BookingService bookingService;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, PaymentEventRepository events, PaymentProvider provider, QuoteRepository quotes,
                          BookingService bookingService, TransactionTemplate tx) {
        this.payments = payments; this.events = events; this.provider = provider; this.quotes = quotes;
        this.bookingService = bookingService; this.tx = tx;
    }

    private record Reservation(UUID bookingId, PaymentEntity payment, boolean created) {}

    // Tentatives non résolues : elles bloquent toute nouvelle tentative sur le devis tant qu'elles ne sont pas réconciliées.
    static final List<PaymentStatus> UNRESOLVED_STATUSES = List.of(
            PaymentStatus.PENDING, PaymentStatus.AUTHORIZED, PaymentStatus.UNKNOWN, PaymentStatus.MANUAL_REVIEW);

    // Trois transactions courtes. Le fournisseur n'est jamais appelé pendant qu'un verrou PostgreSQL est tenu.
    //  1. Réservation : verrous Vehicle → Booking → Quote, enregistrement PENDING, commit.
    //  2. Appel fournisseur hors transaction. Erreur, timeout ou réponse perdue : UNKNOWN (réconciliation requise).
    //  3. Finalisation : statut du paiement et événement.
    public PaymentEntity createForQuote(UUID quoteId, UUID actorCustomerId, java.math.BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        Reservation reservation;
        try {
            reservation = tx.execute(status -> reserve(quoteId, actorCustomerId, amount, method, idempotencyKey));
        } catch (DataIntegrityViolationException ex) {
            // Transaction annulée ici. Une requête identique concurrente a pu committer le paiement : on le renvoie (rejeu idempotent, sans second appel fournisseur).
            var winner = tx.execute(status -> payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(actorCustomerId, quoteId, idempotencyKey).orElse(null));
            // Pas de paiement avec cette clé : la violation vient d'une autre contrainte (ex. unicité par devis) ; elle est classée par ApiExceptionHandler.
            if (winner == null) throw ex;
            if (winner.getMethod() != method || winner.getAmount().compareTo(amount) != 0) {
                throw new PaymentException("IDEMPOTENCY_CONFLICT", "Cette cle correspond a un autre paiement.");
            }
            return winner;
        }
        if (!reservation.created()) return reservation.payment();

        UUID bookingId = reservation.bookingId();
        var payment = reservation.payment();
        PaymentResult result;
        try {
            result = provider.process(payment);
        } catch (RuntimeException ex) {
            // Timeout, erreur ou réponse perdue : résultat inconnu. Jamais un refus définitif.
            return tx.execute(status -> markUnknown(payment, "PROVIDER_UNKNOWN",
                    "Résultat fournisseur inconnu, réconciliation requise"));
        }
        try {
            return tx.execute(status -> finish(payment.getId(), bookingId, result, "PROCESSED", result.rawMessage()));
        } catch (RuntimeException ex) {
            // La finalisation a échoué après la réponse fournisseur : la référence est persistée séparément
            // et le paiement passe en UNKNOWN. La capture n'est confirmée que par la réconciliation.
            try {
                tx.execute(status -> recordUnfinished(payment.getId(), bookingId, result.transactionRef()));
            } catch (RuntimeException recoveryEx) {
                ex.addSuppressed(recoveryEx);
            }
            throw ex;
        }
    }

    // Ordre de verrouillage global : Vehicle → Booking → Quote (docs/architecture/booking-lock-order.md).
    private Reservation reserve(UUID quoteId, UUID actorCustomerId, java.math.BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        UUID bookingId = quotes.findBookingIdForCustomer(quoteId, actorCustomerId)
                .orElseThrow(() -> new PaymentException("QUOTE_NOT_FOUND", "Le devis est introuvable."));
        var booking = bookingService.lockForCommercialOperation(bookingId);
        QuoteEntity quote = quotes.findLockedById(quoteId).orElseThrow(() -> new PaymentException("QUOTE_NOT_FOUND", "Le devis est introuvable."));
        if (quote.getStatus() != QuoteStatus.ACCEPTED) throw new PaymentException("QUOTE_NOT_ACCEPTED", "QUOTE_NOT_ACCEPTED: Le devis doit etre accepte.");
        if (amount == null || amount.signum() <= 0 || method == null || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new PaymentException("INVALID_PAYMENT", "Les donnees du paiement sont invalides.");
        }
        // Idempotence délimitée par client, devis et clé : une relecture identique renvoie le paiement existant.
        // Elle est évaluée avant le contrôle du montant pour qu'un rejeu avec un autre montant soit un conflit d'idempotence.
        var existing = payments.findByCustomerIdAndQuote_IdAndIdempotencyKey(actorCustomerId, quoteId, idempotencyKey);
        if (existing.isPresent()) {
            var previous = existing.get();
            if (previous.getMethod() != method || previous.getAmount().compareTo(amount) != 0) {
                throw new PaymentException("IDEMPOTENCY_CONFLICT", "Cette cle correspond a un autre paiement.");
            }
            return new Reservation(bookingId, previous, false);
        }

        if (amount.compareTo(quote.getTotalAmount()) != 0) throw new PaymentException("PAYMENT_AMOUNT_MISMATCH", "Le montant doit correspondre au devis accepte.");

        // Un seul paiement capturé par devis, et une seule tentative non résolue à la fois (PENDING, UNKNOWN, MANUAL_REVIEW).
        if (booking.getStatus() != BookingStatus.PENDING) throw new PaymentException("BOOKING_NOT_PAYABLE", "Cette réservation n'est plus en attente de paiement.");
        if (payments.existsByQuote_IdAndStatusIn(quoteId, List.of(PaymentStatus.CAPTURED))) {
            throw new PaymentException("QUOTE_ALREADY_PAID", "Ce devis a deja ete paye.");
        }
        if (payments.existsByQuote_IdAndStatusIn(quoteId, UNRESOLVED_STATUSES)) {
            throw new PaymentException("PAYMENT_IN_PROGRESS", "Un paiement de ce devis est deja en cours de traitement.");
        }

        var payment = new PaymentEntity(quote, actorCustomerId, quote.getTotalAmount(), quote.getCurrency(), method, idempotencyKey);
        payment.setBooking(quote.getBooking());
        payments.saveAndFlush(payment);
        events.saveAndFlush(new PaymentEventEntity(payment, "CREATED", "Payment created"));
        return new Reservation(bookingId, payment, true);
    }

    // Ordre Vehicle → Booking → Payment : le verrou Booking précède toute écriture sur la tentative.
    private PaymentEntity finish(UUID paymentId, UUID bookingId, PaymentResult result, String eventType, String message) {
        bookingService.lockForCommercialOperation(bookingId);
        var payment = payments.findForUpdate(paymentId).orElseThrow(() -> new IllegalStateException("Tentative introuvable : " + paymentId));
        if (result != null && payment.getStatus() == PaymentStatus.CAPTURED) {
            // Capture déjà confirmée par la réconciliation (ordre inverse) : jamais réappliquée, jamais écrasée.
            if (result.status() == PaymentStatus.CAPTURED && Objects.equals(payment.getTransactionRef(), result.transactionRef())) {
                events.saveAndFlush(new PaymentEventEntity(payment, "FINISH_ALREADY_CONFIRMED", "Capture déjà confirmée par réconciliation"));
                return payment;
            }
            throw new PaymentException("PAYMENT_ALREADY_CONFIRMED", "La tentative est déjà confirmée avec une autre preuve fournisseur.");
        }
        if (result != null) {
            payment.setTransactionRef(result.transactionRef());
            payment.transitionTo(result.status());
            if (result.status() == PaymentStatus.CAPTURED) payment.setCapturedAt(Instant.now());
        }
        payments.saveAndFlush(payment);
        events.saveAndFlush(new PaymentEventEntity(payment, eventType, message));
        return payment;
    }

    private PaymentEntity markUnknown(PaymentEntity payment, String eventType, String message) {
        payment.transitionTo(PaymentStatus.UNKNOWN);
        payments.saveAndFlush(payment);
        events.saveAndFlush(new PaymentEventEntity(payment, eventType, message));
        return payment;
    }

    private PaymentEntity recordUnfinished(UUID paymentId, UUID bookingId, String transactionRef) {
        // Relecture sous verrou (même ordre Vehicle → Booking → Payment) : la finalisation a été annulée, l'état en mémoire n'est pas fiable.
        bookingService.lockForCommercialOperation(bookingId);
        var payment = payments.findForUpdate(paymentId).orElseThrow();
        // Une capture déjà confirmée n'est jamais rétrogradée ni réécrite par une finalisation d'échec.
        if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.UNKNOWN) {
            payment.setTransactionRef(transactionRef);
            if (payment.getStatus() == PaymentStatus.PENDING) payment.transitionTo(PaymentStatus.UNKNOWN);
        }
        payments.saveAndFlush(payment);
        events.saveAndFlush(new PaymentEventEntity(payment, "FINISH_FAILED", "Finalisation échouée après réponse fournisseur"));
        return payment;
    }
}

