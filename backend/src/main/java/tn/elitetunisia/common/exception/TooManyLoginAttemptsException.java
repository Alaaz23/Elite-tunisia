package tn.elitetunisia.common.exception;

import org.springframework.http.HttpStatus;

/** Trop d'échecs de connexion pour cette combinaison e-mail / adresse IP ; le client doit patienter. */
public class TooManyLoginAttemptsException extends DomainException {
    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("LOGIN_THROTTLED", HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
