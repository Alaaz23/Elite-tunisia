package tn.elitetunisia.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier prévisible, traduite en réponse HTTP typée (au lieu d'un 500).
 * Le message reste le code, ce qui préserve les tests et appelants qui comparent {@code getMessage()}.
 */
public class DomainException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public DomainException(String code, HttpStatus status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public static DomainException notFound(String code) { return new DomainException(code, HttpStatus.NOT_FOUND); }
    public static DomainException conflict(String code) { return new DomainException(code, HttpStatus.CONFLICT); }
    public static DomainException invalid(String code) { return new DomainException(code, HttpStatus.BAD_REQUEST); }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
