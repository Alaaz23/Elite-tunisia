package tn.elitetunisia.common.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Cette adresse e-mail est déjà utilisée.");
    }
}