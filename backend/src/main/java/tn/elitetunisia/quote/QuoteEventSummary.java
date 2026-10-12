package tn.elitetunisia.quote;
import java.time.Instant;
import java.util.UUID;
public record QuoteEventSummary(UUID actorId, String action, String message, Instant timestamp) { }