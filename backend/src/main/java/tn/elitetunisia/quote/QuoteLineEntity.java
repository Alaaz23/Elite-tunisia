package tn.elitetunisia.quote;
import java.math.BigDecimal;
import java.util.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@Entity @Table(name = "quote_lines")
public class QuoteLineEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_id") private QuoteEntity quote;
    @Column(nullable = false, length = 40) private String type;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 3) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) private Map<String, String> metadata;
    @Column(nullable = false) private int position;
    protected QuoteLineEntity() { }
    public QuoteLineEntity(QuoteEntity quote, String type, String description, int quantity, BigDecimal unitPrice, Map<String, String> metadata, int position) {
        id = UUID.randomUUID(); this.quote = quote; this.type = type; this.description = description;
        this.quantity = quantity; this.unitPrice = unitPrice; this.amount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (type.equals("DISCOUNT")) amount = amount.negate();
        this.metadata = Map.copyOf(metadata); this.position = position;
    }
    public QuoteLineSummary summary() { return new QuoteLineSummary(id, type, description, quantity, unitPrice, amount, metadata, position); }
    public String getType() { return type; } public BigDecimal getAmount() { return amount; }
    public BigDecimal getUnitPrice() { return unitPrice; } public int getQuantity() { return quantity; }
}