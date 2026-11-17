package tn.elitetunisia.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentEntityTransitionUnitTest {

    private static PaymentEntity pending() {
        return new PaymentEntity(null, UUID.randomUUID(), new BigDecimal("10.000"), "TND", PaymentMethod.CARD, "k");
    }

    @Test
    void legalTransitionIsApplied() {
        var p = pending();
        p.transitionTo(PaymentStatus.UNKNOWN);
        p.transitionTo(PaymentStatus.CAPTURED);
        assertEquals(PaymentStatus.CAPTURED, p.getStatus());
    }

    @Test
    void illegalTransitionIsRejectedAndStateUnchanged() {
        var captured = pending();
        captured.transitionTo(PaymentStatus.CAPTURED);
        assertThrows(IllegalStateException.class, () -> captured.transitionTo(PaymentStatus.CAPTURED));
        assertThrows(IllegalStateException.class, () -> captured.transitionTo(PaymentStatus.FAILED));
        assertEquals(PaymentStatus.CAPTURED, captured.getStatus());

        var failed = pending();
        failed.transitionTo(PaymentStatus.FAILED);
        assertThrows(IllegalStateException.class, () -> failed.transitionTo(PaymentStatus.CAPTURED));
        assertEquals(PaymentStatus.FAILED, failed.getStatus());
    }
}
