package tn.elitetunisia;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tn.elitetunisia.payment.PaymentStatus;

public class PaymentUnitTests {
    @Test void enumContainsExpectedStates() {
        assertTrue(java.util.EnumSet.of(PaymentStatus.PENDING, PaymentStatus.CAPTURED, PaymentStatus.FAILED).contains(PaymentStatus.CAPTURED));
    }
}
