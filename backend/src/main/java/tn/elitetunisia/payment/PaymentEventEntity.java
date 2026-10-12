package tn.elitetunisia.payment;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "payment_events")
public class PaymentEventEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id") private PaymentEntity payment;
    @Column(nullable = false, length = 60) private String action;
    @Column(columnDefinition = "text") private String message;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected PaymentEventEntity() {}
    public PaymentEventEntity(PaymentEntity payment, String action, String message) { this.id = UUID.randomUUID(); this.payment = payment; this.action = action; this.message = message; }
    public UUID getId() { return id; }
    public PaymentEntity getPayment() { return payment; }
    public String getAction() { return action; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
