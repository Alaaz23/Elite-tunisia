package tn.elitetunisia.catalogue;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugifier {
    private Slugifier() { }

    public static String slug(String value) {
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) throw new tn.elitetunisia.catalogue.dto.InvalidCatalogueRequestException("Le nom ne permet pas de créer un slug.");
        return normalized;
    }
}