package tn.elitetunisia.user.service;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() { super("Utilisateur introuvable."); }
}