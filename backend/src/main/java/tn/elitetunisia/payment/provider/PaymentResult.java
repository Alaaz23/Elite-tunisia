package tn.elitetunisia.payment.provider;

import tn.elitetunisia.payment.PaymentStatus;

public record PaymentResult(PaymentStatus status, String transactionRef, String rawMessage) {}
