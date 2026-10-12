package tn.elitetunisia.quote;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name = "quote_events")
public class QuoteEventEntity {
    @Id private UUID id;
    @Column(name = "quote_id", nullable = false) private UUID quoteId;
    @Column(name = "actor_id") private UUID actorId;
    @Column(nullable = false, length = 60) private String action;
    @Column(length = 2000) private String message;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected QuoteEventEntity() { }
    public QuoteEventEntity(UUID quoteId, UUID actor, String action, String message) {
        id = UUID.randomUUID(); this.quoteId = quoteId; actorId = actor; this.action = action; this.message = message; createdAt = Instant.now();
    }
    public QuoteEventSummary summary() { return new QuoteEventSummary(actorId, action, message, createdAt); }
}