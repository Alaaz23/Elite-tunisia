package tn.elitetunisia.auth.service;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("La session a expiré. Veuillez vous reconnecter.");
    }
}