package tn.elitetunisia.catalogue.dto;

import java.math.BigDecimal;

import tn.elitetunisia.vehicle.TransmissionType;

public record VehicleSearchRequest(String search, String brand, String category,
        BigDecimal minPrice, BigDecimal maxPrice, Integer passengers,
        TransmissionType transmission, Boolean withDriver, String sort, Integer page, Integer size) { }