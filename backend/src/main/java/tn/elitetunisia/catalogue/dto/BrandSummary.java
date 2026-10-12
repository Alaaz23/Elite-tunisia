package tn.elitetunisia.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

public record BrandSummary(UUID id, String name, String slug, String description, String logoUrl,
        boolean active, Instant createdAt, Instant updatedAt) { }