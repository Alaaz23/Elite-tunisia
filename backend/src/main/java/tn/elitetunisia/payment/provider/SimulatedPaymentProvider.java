package tn.elitetunisia.payment.provider;

import org.springframework.stereotype.Service;
import tn.elitetunisia.payment.PaymentEntity;
import tn.elitetunisia.payment.PaymentStatus;

import java.util.UUID;

@Service
public class SimulatedPaymentProvider implements PaymentProvider {
    @Override
    public PaymentResult process(PaymentEntity payment) {
        // Simple synchronous simulation: succeed for positive amounts
        if (payment.getAmount().doubleValue() <= 0) {
            return new PaymentResult(PaymentStatus.FAILED, null, "Invalid amount");
        }
        String tx = "SIM-" + UUID.randomUUID();
        return new PaymentResult(PaymentStatus.CAPTURED, tx, "simulated-ok");
    }
}
