package tn.elitetunisia.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import tn.elitetunisia.vehicle.FuelType;
import tn.elitetunisia.vehicle.TransmissionType;
import tn.elitetunisia.vehicle.VehicleStatus;

public record VehicleSummary(UUID id, BrandSummary brand, String model, Integer year,
        CategorySummary category, String description, BigDecimal pricePerDay, BigDecimal pricePerWeek,
        BigDecimal priceWithDriver, String currency, Integer passengers, Integer doors,
        TransmissionType transmission, FuelType fuel, boolean withDriverAvailable,
        VehicleStatus status, List<VehicleImageSummary> images, List<String> features,
        Instant createdAt, Instant updatedAt) { }