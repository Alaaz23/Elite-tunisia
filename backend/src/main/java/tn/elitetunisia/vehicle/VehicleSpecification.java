package tn.elitetunisia.vehicle;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import tn.elitetunisia.booking.BookingEntity;
import tn.elitetunisia.booking.BookingStatus;
import tn.elitetunisia.catalogue.dto.VehicleSearchRequest;
import tn.elitetunisia.partner.GuaranteeEntity;
import tn.elitetunisia.partner.GuaranteeStatus;

public final class VehicleSpecification {
    private VehicleSpecification() { }

    /**
     * Véhicules réservables sur [start, end] : aucune réservation CONFIRMED ou IN_PROGRESS chevauchante
     * (une demande PENDING ne bloque jamais) et une garantie ACTIVE couvrant intégralement la période.
     */
    public static Specification<VehicleEntity> availableDuring(Instant start, Instant end) {
        return (root, query, builder) -> {
            var blockingSubquery = query.subquery(UUID.class);
            var booking = blockingSubquery.from(BookingEntity.class);
            blockingSubquery.select(booking.get("id")).where(
                    builder.equal(booking.get("vehicle").get("id"), root.get("id")),
                    booking.get("status").in(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS),
                    builder.lessThan(booking.get("startDateTime"), end),
                    builder.greaterThan(booking.get("endDateTime"), start));

            var guaranteeSubquery = query.subquery(UUID.class);
            var guarantee = guaranteeSubquery.from(GuaranteeEntity.class);
            guaranteeSubquery.select(guarantee.get("id")).where(
                    builder.equal(guarantee.get("vehicle").get("id"), root.get("id")),
                    builder.equal(guarantee.get("status"), GuaranteeStatus.ACTIVE),
                    builder.lessThanOrEqualTo(guarantee.get("startsAt"), start),
                    builder.greaterThanOrEqualTo(guarantee.get("endsAt"), end));

            return builder.and(
                    builder.not(builder.exists(blockingSubquery)),
                    builder.exists(guaranteeSubquery));
        };
    }

    public static Specification<VehicleEntity> matching(VehicleSearchRequest filter, boolean publicOnly) {
        Specification<VehicleEntity> specification = (root, query, builder) -> builder.conjunction();
        if (publicOnly) {
            specification = specification.and((root, query, builder) -> builder.and(
                    builder.isTrue(root.join("brand").get("active")),
                    builder.isTrue(root.join("category").get("active")),
                    builder.equal(root.get("status"), VehicleStatus.AVAILABLE)));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String value = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("model")), value),
                    builder.like(builder.lower(root.join("brand").get("name")), value),
                    builder.like(builder.lower(root.join("category").get("name")), value)));
        }
        if (filter.brand() != null && !filter.brand().isBlank()) {
            String slug = filter.brand().trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, builder) -> builder.equal(root.join("brand").get("slug"), slug));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            String slug = filter.category().trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, builder) -> builder.equal(root.join("category").get("slug"), slug));
        }
        if (filter.minPrice() != null) {
            specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("pricePerDay"), filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("pricePerDay"), filter.maxPrice()));
        }
        if (filter.passengers() != null) {
            specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("passengers"), filter.passengers()));
        }
        if (filter.transmission() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("transmission"), filter.transmission()));
        }
        if (filter.withDriver() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("withDriverAvailable"), filter.withDriver()));
        }
        return specification;
    }
}