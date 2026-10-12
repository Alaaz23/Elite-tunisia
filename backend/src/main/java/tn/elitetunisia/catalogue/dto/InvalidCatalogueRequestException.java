package tn.elitetunisia.catalogue.dto;

public class InvalidCatalogueRequestException extends RuntimeException {
    public InvalidCatalogueRequestException(String message) { super(message); }
}