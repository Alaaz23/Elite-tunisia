package tn.elitetunisia.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import tn.elitetunisia.common.exception.TooManyLoginAttemptsException;

class LoginThrottleTest {
    /** Horloge réglable pour tester la fenêtre glissante sans attendre. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(10), clock);

    @Test
    void allowsAttemptsUntilTheQuotaOfFailuresIsReached() {
        String key = LoginThrottle.key("a@example.test", "10.0.0.1");
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> throttle.checkAllowed(key));
            throttle.recordFailure(key);
        }
        var blocked = assertThrows(TooManyLoginAttemptsException.class, () -> throttle.checkAllowed(key));
        assertEquals("LOGIN_THROTTLED", blocked.getCode());
        assertEquals(429, blocked.getStatus().value());
        assertTrue(blocked.getRetryAfterSeconds() > 0 && blocked.getRetryAfterSeconds() <= 601);
    }

    @Test
    void failuresAreTrackedPerEmailAndPerAddress() {
        String victim = LoginThrottle.key("victim@example.test", "10.0.0.1");
        for (int i = 0; i < 3; i++) throttle.recordFailure(victim);
        assertThrows(TooManyLoginAttemptsException.class, () -> throttle.checkAllowed(victim));
        assertDoesNotThrow(() -> throttle.checkAllowed(LoginThrottle.key("victim@example.test", "10.0.0.2")));
        assertDoesNotThrow(() -> throttle.checkAllowed(LoginThrottle.key("other@example.test", "10.0.0.1")));
    }

    @Test
    void successResetsTheCounter() {
        String key = LoginThrottle.key("a@example.test", "10.0.0.1");
        throttle.recordFailure(key); throttle.recordFailure(key);
        throttle.recordSuccess(key);
        throttle.recordFailure(key); throttle.recordFailure(key);
        assertDoesNotThrow(() -> throttle.checkAllowed(key));
    }

    @Test
    void failuresExpireWhenTheWindowSlides() {
        String key = LoginThrottle.key("a@example.test", "10.0.0.1");
        for (int i = 0; i < 3; i++) throttle.recordFailure(key);
        assertThrows(TooManyLoginAttemptsException.class, () -> throttle.checkAllowed(key));
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertDoesNotThrow(() -> throttle.checkAllowed(key));
        throttle.recordFailure(key);
        assertDoesNotThrow(() -> throttle.checkAllowed(key));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new LoginThrottle(0, Duration.ofMinutes(1), clock));
        assertThrows(IllegalArgumentException.class, () -> new LoginThrottle(3, Duration.ZERO, clock));
    }

    @Test
    void missingAddressStillProducesAStableKey() {
        assertEquals("a@example.test|unknown", LoginThrottle.key("a@example.test", null));
    }
}
