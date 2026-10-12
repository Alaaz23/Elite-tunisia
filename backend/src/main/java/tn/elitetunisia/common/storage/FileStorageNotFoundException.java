package tn.elitetunisia.common.storage;

public class FileStorageNotFoundException extends RuntimeException {
    public FileStorageNotFoundException() { super("Fichier introuvable."); }
}