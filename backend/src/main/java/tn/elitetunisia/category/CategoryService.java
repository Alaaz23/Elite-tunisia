package tn.elitetunisia.category;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.catalogue.CatalogueConflictException;
import tn.elitetunisia.catalogue.CatalogueNotFoundException;
import tn.elitetunisia.catalogue.Slugifier;
import tn.elitetunisia.catalogue.dto.CategorySummary;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.vehicle.VehicleRepository;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    private final VehicleRepository vehicles;
    private final AuditService audit;

    public CategoryService(CategoryRepository categories, VehicleRepository vehicles, AuditService audit) {
        this.categories = categories;
        this.vehicles = vehicles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PagedResponse<CategorySummary> publicList(int page, int size) {
        return page(categories.findAllByActiveTrue(PageRequest.of(safePage(page), safeSize(size), Sort.by("name").ascending())));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CategorySummary> adminList(int page, int size) {
        return page(categories.findAll(PageRequest.of(safePage(page), safeSize(size), Sort.by("name").ascending())));
    }

    @Transactional(readOnly = true)
    public CategorySummary getPublic(UUID id) {
        return categories.findByIdAndActiveTrue(id).map(this::summary).orElseThrow(CatalogueNotFoundException::new);
    }

    @Transactional
    public CategorySummary create(CategoryUpsertRequest request, UUID actorId, String ipAddress) {
        String name = request.name().trim();
        String slug = Slugifier.slug(name);
        if (categories.existsByNameIgnoreCase(name) || categories.existsBySlug(slug)) {
            throw new CatalogueConflictException("Une catégorie avec ce nom existe déjà.");
        }
        CategoryEntity saved = categories.save(new CategoryEntity(name, slug, request.description().trim(),
                request.active() == null || request.active()));
        audit.recordAudit(actorId, "CATEGORY_CREATED", "CATEGORY", saved.getId(), ipAddress, "SUCCESS");
        return summary(saved);
    }

    @Transactional
    public CategorySummary update(UUID id, CategoryUpsertRequest request, UUID actorId, String ipAddress) {
        CategoryEntity category = categories.findById(id).orElseThrow(CatalogueNotFoundException::new);
        String name = request.name().trim();
        String slug = Slugifier.slug(name);
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id) || categories.existsBySlugAndIdNot(slug, id)) {
            throw new CatalogueConflictException("Une catégorie avec ce nom existe déjà.");
        }
        category.update(name, slug, request.description().trim(), request.active() == null ? category.isActive() : request.active());
        audit.recordAudit(actorId, "CATEGORY_UPDATED", "CATEGORY", id, ipAddress, "SUCCESS");
        return summary(category);
    }

    @Transactional
    public void delete(UUID id, UUID actorId, String ipAddress) {
        CategoryEntity category = categories.findById(id).orElseThrow(CatalogueNotFoundException::new);
        if (vehicles.existsByCategoryId(id)) throw new CatalogueConflictException("Cette catégorie possède des véhicules et ne peut pas être supprimée.");
        categories.delete(category);
        audit.recordAudit(actorId, "CATEGORY_DELETED", "CATEGORY", id, ipAddress, "SUCCESS");
    }

    public CategorySummary summary(CategoryEntity category) {
        return new CategorySummary(category.getId(), category.getName(), category.getSlug(), category.getDescription(),
                category.isActive(), category.getCreatedAt(), category.getUpdatedAt());
    }

    private PagedResponse<CategorySummary> page(Page<CategoryEntity> page) {
        return new PagedResponse<>(page.map(this::summary).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private int safePage(int page) { return Math.max(page, 0); }
    private int safeSize(int size) { return Math.min(Math.max(size, 1), 100); }
}