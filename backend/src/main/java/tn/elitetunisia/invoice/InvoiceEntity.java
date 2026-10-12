package tn.elitetunisia.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.quote.QuoteEntity;

@Entity
@Table(name = "invoices")
public class InvoiceEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 60) private String reference;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quote_id") private QuoteEntity quote;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 3) private BigDecimal discountAmount;
    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 3) private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 3) private BigDecimal totalAmount;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
    @Column(name = "due_at") private Instant dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private InvoiceStatus status = InvoiceStatus.ISSUED;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("position ASC") private List<InvoiceLineEntity> lines = new ArrayList<>();

    protected InvoiceEntity() {}

    public InvoiceEntity(String reference, QuoteEntity quote, BookingEntity booking, UUID customerId, String currency, java.math.BigDecimal subtotal, java.math.BigDecimal discount, java.math.BigDecimal tax, java.math.BigDecimal total, Instant issuedAt, Instant dueAt) {
        this.id = UUID.randomUUID(); this.reference = reference; this.quote = quote; this.booking = booking; this.customerId = customerId; this.currency = currency;
        this.subtotal = subtotal; this.discountAmount = discount; this.taxAmount = tax; this.totalAmount = total; this.issuedAt = issuedAt; this.dueAt = dueAt;
    }

    public void addLine(InvoiceLineEntity line) { line.setInvoice(this); this.lines.add(line); }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public BookingEntity getBooking() { return booking; }
    public QuoteEntity getQuote() { return quote; }
    public UUID getCustomerId() { return customerId; }
    public String getCurrency() { return currency; }
    public java.math.BigDecimal getSubtotal() { return subtotal; }
    public java.math.BigDecimal getDiscountAmount() { return discountAmount; }
    public java.math.BigDecimal getTaxAmount() { return taxAmount; }
    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getDueAt() { return dueAt; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public List<InvoiceLineEntity> getLines() { return lines; }
}
