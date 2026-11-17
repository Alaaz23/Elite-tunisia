package tn.elitetunisia.common.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import tn.elitetunisia.catalogue.dto.InvalidCatalogueRequestException;

/**
 * Normalise une demande de pagination admin : taille bornée, tri limité à une liste blanche
 * et tie-breaker stable sur l'identifiant. Un champ de tri inconnu est rejeté avec un 400.
 */
public final class AdminPageRequest {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private AdminPageRequest() {
    }

    public static Pageable of(Pageable requested, Set<String> sortableProperties, String defaultSortProperty) {
        return of(requested, sortableProperties, defaultSortProperty, Sort.Direction.DESC);
    }

    public static Pageable of(Pageable requested, Set<String> sortableProperties, String defaultSortProperty,
            Sort.Direction defaultDirection) {
        if (requested.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_SIZE, defaultSort(defaultSortProperty, defaultDirection));
        }
        int size = Math.min(Math.max(requested.getPageSize(), 1), MAX_SIZE);
        Sort sort = requested.getSort().isUnsorted()
                ? defaultSort(defaultSortProperty, defaultDirection)
                : whitelisted(requested.getSort(), sortableProperties);
        return PageRequest.of(requested.getPageNumber(), size, sort);
    }

    private static Sort defaultSort(String property, Sort.Direction direction) {
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static Sort whitelisted(Sort requested, Set<String> sortableProperties) {
        Sort sort = Sort.unsorted();
        for (Sort.Order order : requested) {
            if (!sortableProperties.contains(order.getProperty())) {
                throw new InvalidCatalogueRequestException("Critère de tri non pris en charge.");
            }
            sort = sort.and(Sort.by(order));
        }
        return sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
