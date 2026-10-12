package tn.elitetunisia.quote;
import jakarta.validation.constraints.Size;
public record QuoteMessage(@Size(max = 2000) String message) { }