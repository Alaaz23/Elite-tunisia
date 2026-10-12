package tn.elitetunisia.catalogue;

public class CatalogueNotFoundException extends RuntimeException {
    public CatalogueNotFoundException() { super("L'élément du catalogue est introuvable."); }
}