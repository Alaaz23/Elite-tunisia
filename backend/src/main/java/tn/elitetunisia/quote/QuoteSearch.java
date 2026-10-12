package tn.elitetunisia.quote;
import java.time.Instant;
import java.util.UUID;
public record QuoteSearch(String reference, UUID customer, UUID booking, UUID vehicle, QuoteStatus status,
        Instant date, Instant validUntil, Integer page, Integer size) { }