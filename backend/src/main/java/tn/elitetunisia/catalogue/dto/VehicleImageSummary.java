package tn.elitetunisia.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

public record VehicleImageSummary(UUID id, String url, String altText, short position,
        boolean isPrimary, Instant createdAt) { }