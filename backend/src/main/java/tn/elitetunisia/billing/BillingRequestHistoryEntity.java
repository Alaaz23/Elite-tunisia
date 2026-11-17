package tn.elitetunisia.billing;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_request_history")
public class BillingRequestHistoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "request_id", nullable = false, updatable = false) private UUID requestId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(nullable = false, length = 30, updatable = false) private String event;
    @Column(name = "attempt_no", updatable = false) private Integer attemptNo;
    @Column(length = 1000, updatable = false) private String error;
    @Column(name = "actor_id", updatable = false) private UUID actorId;
    @Column(length = 500, updatable = false) private String reason;

    protected BillingRequestHistoryEntity() {}

    public BillingRequestHistoryEntity(UUID requestId, Instant occurredAt, String event, Integer attemptNo,
            String error, UUID actorId, String reason) {
        this.requestId = requestId; this.occurredAt = occurredAt; this.event = event; this.attemptNo = attemptNo;
        this.error = error; this.actorId = actorId; this.reason = reason;
    }

    public Long getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getEvent() { return event; }
    public Integer getAttemptNo() { return attemptNo; }
    public String getError() { return error; }
    public UUID getActorId() { return actorId; }
    public String getReason() { return reason; }
}
