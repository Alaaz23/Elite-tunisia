package tn.elitetunisia.payment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public PaymentService(PaymentRepository payments, PaymentEventRepository events, PaymentProvider provider, QuoteRepository quotes) {
        this.payments = payments; this.events = events; this.provider = provider; this.quotes = quotes;
    }

    @Transactional
    public PaymentEntity createForQuote(UUID quoteId, UUID actorCustomerId, java.math.BigDecimal amount, PaymentMethod method, String idempotencyKey) {
        QuoteEntity quote = quotes.findLockedById(quoteId).orElseThrow(() -> new PaymentException("QUOTE_NOT_FOUND", "Le devis est introuvable."));
        if (!quote.getCustomerId().equals(actorCustomerId)) throw new PaymentException("QUOTE_NOT_FOUND", "Le devis est introuvable.");
        if (quote.getStatus() != QuoteStatus.ACCEPTED) throw new PaymentException("QUOTE_NOT_ACCEPTED", "QUOTE_NOT_ACCEPTED: Le devis doit etre accepte.");
        if (amount == null || amount.signum() <= 0 || method == null || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new PaymentException("INVALID_PAYMENT", "Les donnees du paiement sont invalides.");
        }
        if (amount.compareTo(quote.getTotalAmount()) != 0) throw new PaymentException("PAYMENT_AMOUNT_MISMATCH", "Le montant doit correspondre au devis accepte.");
        // First try to detect existing payment (fast path)
        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            var previous = existing.get();
            if (!previous.getQuote().getId().equals(quoteId) || !previous.getCustomerId().equals(actorCustomerId)
                    || previous.getMethod() != method || previous.getAmount().compareTo(quote.getTotalAmount()) != 0) {
                throw new PaymentException("IDEMPOTENCY_CONFLICT", "Cette cle correspond a un autre paiement.");
            }
            return previous;
        }

        // Not found: create and save. If concurrent request inserts first, handle unique constraint violation by fetching existing record.
        var payment = new PaymentEntity(quote, actorCustomerId, quote.getTotalAmount(), quote.getCurrency(), method, idempotencyKey);
        payment.setBooking(quote.getBooking());
        try {
            payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            // Possible unique constraint violation on idempotency_key from concurrent request
            var maybe = payments.findByIdempotencyKey(idempotencyKey);
            if (maybe.isPresent()) {
                var previous = maybe.get();
                if (!previous.getQuote().getId().equals(quoteId) || !previous.getCustomerId().equals(actorCustomerId)
                        || previous.getMethod() != method || previous.getAmount().compareTo(quote.getTotalAmount()) != 0) {
                    throw new PaymentException("IDEMPOTENCY_CONFLICT", "Cette cle correspond a un autre paiement.");
                }
                return previous;
            }
            // rethrow if we cannot resolve
            throw ex;
        }
        events.saveAndFlush(new PaymentEventEntity(payment, "CREATED", "Payment created"));

        // synchronous processing via provider
        PaymentResult result = provider.process(payment);
        payment.setTransactionRef(result.transactionRef());
        payment.setStatus(result.status());
        if (result.status() == PaymentStatus.CAPTURED) payment.setCapturedAt(Instant.now());
        payments.saveAndFlush(payment);
        events.saveAndFlush(new PaymentEventEntity(payment, "PROCESSED", result.rawMessage()));
        return payment;
    }
}
