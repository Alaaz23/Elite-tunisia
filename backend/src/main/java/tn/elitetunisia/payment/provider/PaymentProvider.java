package tn.elitetunisia.payment.provider;

import java.util.Optional;
import tn.elitetunisia.payment.PaymentEntity;

public interface PaymentProvider {
    PaymentResult process(PaymentEntity payment);

    /**
     * Consultation du résultat d'une opération par clé d'idempotence. Vide = aucune preuve :
     * le paiement reste UNKNOWN. Par défaut, aucun fournisseur ne fournit de preuve.
     */
    default Optional<PaymentResult> lookup(String idempotencyKey) {
        return Optional.empty();
    }
}
