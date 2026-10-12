package tn.elitetunisia.quote;
import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.*;
@Entity @Table(name = "services")
public class ConfiguredServiceEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, precision = 12, scale = 3) private BigDecimal price;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false, length = 3) private String currency;
    protected ConfiguredServiceEntity() { }
    public ConfiguredServiceEntity(String name, String description, BigDecimal price, String currency) {
        id = UUID.randomUUID(); this.name = name; this.description = description; this.price = price; this.currency = currency; active = true;
    }
    public UUID getId() { return id; } public String getName() { return name; } public BigDecimal getPrice() { return price; } public String getCurrency() { return currency; } public boolean isActive() { return active; }
}