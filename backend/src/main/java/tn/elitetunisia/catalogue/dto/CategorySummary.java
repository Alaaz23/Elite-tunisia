package tn.elitetunisia.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

public record CategorySummary(UUID id, String name, String slug, String description,
        boolean active, Instant createdAt, Instant updatedAt) { }