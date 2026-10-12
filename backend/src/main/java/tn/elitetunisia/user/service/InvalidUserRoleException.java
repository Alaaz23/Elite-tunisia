package tn.elitetunisia.user.service;

public class InvalidUserRoleException extends RuntimeException {
    public InvalidUserRoleException() { super("Le rôle demandé n'est pas autorisé."); }
}