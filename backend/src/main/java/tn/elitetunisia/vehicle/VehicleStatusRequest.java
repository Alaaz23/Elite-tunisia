package tn.elitetunisia.vehicle;

import jakarta.validation.constraints.NotNull;

public record VehicleStatusRequest(@NotNull VehicleStatus status) { }