package tn.elitetunisia.invoice;

import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLineEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "invoice_id") private InvoiceEntity invoice;
    @Column(nullable = false, length = 40) private String type;
    @Column(nullable = false, length = 400) private String description;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 3) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) private Map<String, String> metadata;
    @Column(nullable = false) private int position;

    protected InvoiceLineEntity() {}
    public InvoiceLineEntity(String type, String description, int quantity, BigDecimal unitPrice, Map<String,String> metadata, int position) {
        this.id = UUID.randomUUID(); this.type = type; this.description = description; this.quantity = quantity; this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(java.math.BigDecimal.valueOf(quantity)); this.metadata = Map.copyOf(metadata); this.position = position;
    }
    public void setInvoice(InvoiceEntity invoice) { this.invoice = invoice; }
    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public java.math.BigDecimal getAmount() { return amount; }
    public java.math.BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public int getPosition() { return position; }
    public java.util.Map<String,String> getMetadata() { return metadata; }
}
