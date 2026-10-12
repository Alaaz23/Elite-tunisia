package tn.elitetunisia.quote;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
public record QuoteLineSummary(UUID id, String type, String description, int quantity, BigDecimal unitPrice,
        BigDecimal amount, Map<String, String> metadata, int position) { }