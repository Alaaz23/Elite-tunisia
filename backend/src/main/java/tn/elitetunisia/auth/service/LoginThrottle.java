package tn.elitetunisia.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tn.elitetunisia.common.exception.TooManyLoginAttemptsException;

/**
 * Limite les échecs de connexion par combinaison e-mail + adresse IP (fenêtre glissante).
 * La clé inclut l'IP pour qu'un tiers ne puisse pas verrouiller le compte d'une victime depuis ailleurs.
 *
 * Limites connues : l'état est en mémoire (une seule instance ; perdu au redémarrage) et un attaquant
 * distribué sur de nombreuses IP n'est pas freiné. Derrière un proxy, {@code getRemoteAddr()} doit refléter
 * l'IP du client (configuration {@code server.forward-headers-strategy} à définir au déploiement).
 */
@Component
public class LoginThrottle {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginThrottle(@Value("${app.security.login-throttle.max-failures:5}") int maxFailures,
            @Value("${app.security.login-throttle.window-seconds:900}") long windowSeconds) {
        this(maxFailures, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
    }

    LoginThrottle(int maxFailures, Duration window, Clock clock) {
        if (maxFailures < 1 || window.isZero() || window.isNegative()) throw new IllegalArgumentException("Invalid login throttle configuration");
        this.maxFailures = maxFailures; this.window = window; this.clock = clock;
    }

    public static String key(String email, String ipAddress) {
        return email + "|" + (ipAddress == null ? "unknown" : ipAddress);
    }

    /** Lève {@link TooManyLoginAttemptsException} si le quota d'échecs est atteint dans la fenêtre. */
    public void checkAllowed(String key) {
        Instant now = clock.instant();
        long[] retryAfter = {0};
        failures.computeIfPresent(key, (k, attempts) -> {
            purge(attempts, now);
            if (attempts.isEmpty()) return null;
            if (attempts.size() >= maxFailures) {
                retryAfter[0] = Math.max(1, Duration.between(now, attempts.peekFirst().plus(window)).toSeconds() + 1);
            }
            return attempts;
        });
        if (retryAfter[0] > 0) throw new TooManyLoginAttemptsException(retryAfter[0]);
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (failures.size() >= MAX_TRACKED_KEYS) evictExpired(now);
        failures.compute(key, (k, attempts) -> {
            Deque<Instant> deque = attempts == null ? new ArrayDeque<>() : attempts;
            purge(deque, now);
            deque.addLast(now);
            while (deque.size() > maxFailures) deque.pollFirst();
            return deque;
        });
    }

    public void recordSuccess(String key) { failures.remove(key); }

    private void purge(Deque<Instant> attempts, Instant now) {
        Instant limit = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(limit)) attempts.pollFirst();
    }

    private void evictExpired(Instant now) {
        for (String key : failures.keySet()) {
            failures.computeIfPresent(key, (k, attempts) -> { purge(attempts, now); return attempts.isEmpty() ? null : attempts; });
        }
    }
}
