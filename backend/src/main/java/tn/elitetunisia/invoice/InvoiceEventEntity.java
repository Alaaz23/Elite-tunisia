package tn.elitetunisia.invoice;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "invoice_events")
public class InvoiceEventEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id") private InvoiceEntity invoice;
    @Column(nullable = false, length = 60) private String action;
    @Column(columnDefinition = "text") private String message;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected InvoiceEventEntity() {}
    public InvoiceEventEntity(InvoiceEntity invoice, String action, String message) { this.id = UUID.randomUUID(); this.invoice = invoice; this.action = action; this.message = message; }
    public UUID getId() { return id; }
    public InvoiceEntity getInvoice() { return invoice; }
    public String getAction() { return action; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
