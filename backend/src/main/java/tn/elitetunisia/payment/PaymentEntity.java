package tn.elitetunisia.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.quote.QuoteEntity;

@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_id") private QuoteEntity quote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentMethod method;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentStatus status = PaymentStatus.PENDING;
    @Column(name = "idempotency_key", length = 100) private String idempotencyKey;
    @Column(name = "transaction_ref", length = 128) private String transactionRef;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "captured_at") private Instant capturedAt;
    @Column(name = "refunded_at") private Instant refundedAt;
    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 3) private BigDecimal refundedAmount = BigDecimal.ZERO;

    protected PaymentEntity() {}
    public PaymentEntity(QuoteEntity quote, java.util.UUID customerId, BigDecimal amount, String currency, PaymentMethod method, String idempotencyKey) {
        this.id = UUID.randomUUID(); this.quote = quote; this.customerId = customerId; this.amount = amount; this.currency = currency; this.method = method; this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public QuoteEntity getQuote() { return quote; }
    public BookingEntity getBooking() { return booking; }
    public void setBooking(BookingEntity booking) { this.booking = booking; }
    public java.util.UUID getCustomerId() { return customerId; }
    public java.math.BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public java.math.BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(java.math.BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }
}
