package tn.elitetunisia.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "billing_requests")
public class BillingRequestEntity {
    public static final String KIND_MAIN_INVOICE = "MAIN_INVOICE";

    /** Délai avant la tentative suivante, indexé par le nombre d'échecs du cycle courant (1 → 1 min, 4 → 1 h). */
    public static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));

    public enum Status { PENDING, DONE, DEAD }

    @Id private UUID id;
    @Column(name = "booking_id", nullable = false, updatable = false) private UUID bookingId;
    @Column(nullable = false, length = 40, updatable = false) private String kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(name = "cycle_attempts", nullable = false) private int cycleAttempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "last_attempt_at") private Instant lastAttemptAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "invoice_id") private UUID invoiceId;
    @Column(name = "requested_by", nullable = false, updatable = false) private UUID requestedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected BillingRequestEntity() {}

    public BillingRequestEntity(UUID bookingId, String kind, UUID requestedBy, Instant now) {
        this.id = UUID.randomUUID(); this.bookingId = bookingId; this.kind = kind; this.requestedBy = requestedBy;
        this.status = Status.PENDING; this.attempts = 0; this.cycleAttempts = 0;
        this.nextAttemptAt = now; this.createdAt = now;
    }

    /** Vrai si la demande est en attente et que son délai de reprise est échu. */
    public boolean isDue(Instant now) {
        return status == Status.PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
    }

    public void markDone(UUID invoiceId, Instant now) {
        this.status = Status.DONE; this.invoiceId = invoiceId; this.completedAt = now;
        this.lastAttemptAt = now; this.lastError = null; this.nextAttemptAt = null;
    }

    /**
     * Enregistre un échec. Après {@code maxAttempts} échecs du cycle courant, la demande passe à DEAD ;
     * sinon la prochaine tentative est planifiée selon {@link #BACKOFF}. L'historique n'est jamais effacé.
     */
    public void recordFailure(String error, Instant now, int maxAttempts) {
        this.attempts++;
        this.cycleAttempts++;
        this.lastAttemptAt = now;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (this.cycleAttempts >= maxAttempts) {
            this.status = Status.DEAD;
            this.nextAttemptAt = null;
        } else {
            this.nextAttemptAt = now.plus(BACKOFF.get(Math.min(this.cycleAttempts, BACKOFF.size()) - 1));
        }
    }

    /** Reprise administrative depuis DEAD : nouveau cycle de tentatives, historique conservé. */
    public void retryFromDead(Instant now) {
        if (status != Status.DEAD) throw new IllegalStateException("Only DEAD billing requests can be retried.");
        this.status = Status.PENDING;
        this.cycleAttempts = 0;
        this.nextAttemptAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public String getKind() { return kind; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public int getCycleAttempts() { return cycleAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public String getLastError() { return lastError; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
