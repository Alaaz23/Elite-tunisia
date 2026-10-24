package tn.elitetunisia.customer;

import java.time.Instant;
import java.util.UUID;

public record AdminCustomerSummary(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        String customerLevel,
        String email,
        boolean enabled,
        Instant createdAt) { }
