package tn.elitetunisia.quote;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceResponse(UUID id, String name, String description, BigDecimal price, String currency, boolean active) {
    public static ServiceResponse from(ConfiguredServiceEntity s) {
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getPrice(), s.getCurrency(), s.isActive());
    }
}
