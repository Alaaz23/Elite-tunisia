package tn.elitetunisia.payment.provider;

import tn.elitetunisia.payment.PaymentEntity;

public interface PaymentProvider {
    PaymentResult process(PaymentEntity payment);
}
