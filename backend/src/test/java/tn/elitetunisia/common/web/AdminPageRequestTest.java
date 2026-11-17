package tn.elitetunisia.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import tn.elitetunisia.catalogue.dto.InvalidCatalogueRequestException;

class AdminPageRequestTest {
    private static final Set<String> SORTABLE = Set.of("name", "createdAt");

    @Test
    void rejectsUnknownSortPropertyInsteadOfPassingItToTheRepository() {
        Pageable requested = PageRequest.of(0, 10, Sort.by("passwordHash"));
        assertThrows(InvalidCatalogueRequestException.class,
                () -> AdminPageRequest.of(requested, SORTABLE, "createdAt"));
    }

    @Test
    void clampsSizeAndKeepsStableIdTieBreaker() {
        Pageable result = AdminPageRequest.of(PageRequest.of(2, 100_000, Sort.by("name").ascending()),
                SORTABLE, "createdAt");

        assertEquals(2, result.getPageNumber());
        assertEquals(AdminPageRequest.MAX_SIZE, result.getPageSize());
        assertEquals(Sort.by("name").ascending().and(Sort.by("id").ascending()), result.getSort());
    }

    @Test
    void usesDefaultSortWhenNoSortIsRequested() {
        Pageable result = AdminPageRequest.of(PageRequest.of(1, 5), SORTABLE, "createdAt");

        assertEquals(1, result.getPageNumber());
        assertEquals(5, result.getPageSize());
        assertEquals(Sort.by("createdAt").descending().and(Sort.by("id").ascending()), result.getSort());
    }
}
