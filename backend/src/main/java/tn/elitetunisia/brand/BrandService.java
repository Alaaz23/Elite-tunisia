package tn.elitetunisia.brand;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.elitetunisia.catalogue.CatalogueConflictException;
import tn.elitetunisia.catalogue.CatalogueNotFoundException;
import tn.elitetunisia.catalogue.Slugifier;
import tn.elitetunisia.catalogue.dto.BrandSummary;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.vehicle.VehicleRepository;

@Service
public class BrandService {
    private final BrandRepository brands;
    private final VehicleRepository vehicles;
    private final AuditService audit;

    public BrandService(BrandRepository brands, VehicleRepository vehicles, AuditService audit) {
        this.brands = brands;
        this.vehicles = vehicles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PagedResponse<BrandSummary> publicList(int page, int size) {
        return page(brands.findAllByActiveTrue(PageRequest.of(safePage(page), safeSize(size), Sort.by("name").ascending())));
    }

    @Transactional(readOnly = true)
    public PagedResponse<BrandSummary> adminList(int page, int size) {
        return page(brands.findAll(PageRequest.of(safePage(page), safeSize(size), Sort.by("name").ascending())));
    }

    @Transactional(readOnly = true)
    public BrandSummary getPublic(UUID id) {
        return brands.findByIdAndActiveTrue(id).map(this::summary).orElseThrow(CatalogueNotFoundException::new);
    }

    @Transactional
    public BrandSummary create(BrandUpsertRequest request, UUID actorId, String ipAddress) {
        String name = request.name().trim();
        String slug = Slugifier.slug(name);
        if (brands.existsByNameIgnoreCase(name) || brands.existsBySlug(slug)) {
            throw new CatalogueConflictException("Une marque avec ce nom existe déjà.");
        }
        BrandEntity saved = brands.save(new BrandEntity(name, slug, request.description().trim(), request.logoUrl(),
                request.active() == null || request.active()));
        audit.recordAudit(actorId, "BRAND_CREATED", "BRAND", saved.getId(), ipAddress, "SUCCESS");
        return summary(saved);
    }

    @Transactional
    public BrandSummary update(UUID id, BrandUpsertRequest request, UUID actorId, String ipAddress) {
        BrandEntity brand = brands.findById(id).orElseThrow(CatalogueNotFoundException::new);
        String name = request.name().trim();
        String slug = Slugifier.slug(name);
        if (brands.existsByNameIgnoreCaseAndIdNot(name, id) || brands.existsBySlugAndIdNot(slug, id)) {
            throw new CatalogueConflictException("Une marque avec ce nom existe déjà.");
        }
        brand.update(name, slug, request.description().trim(), request.logoUrl(),
                request.active() == null ? brand.isActive() : request.active());
        audit.recordAudit(actorId, "BRAND_UPDATED", "BRAND", id, ipAddress, "SUCCESS");
        return summary(brand);
    }

    @Transactional
    public void delete(UUID id, UUID actorId, String ipAddress) {
        BrandEntity brand = brands.findById(id).orElseThrow(CatalogueNotFoundException::new);
        if (vehicles.existsByBrandId(id)) throw new CatalogueConflictException("Cette marque possède des véhicules et ne peut pas être supprimée.");
        brands.delete(brand);
        audit.recordAudit(actorId, "BRAND_DELETED", "BRAND", id, ipAddress, "SUCCESS");
    }

    public BrandSummary summary(BrandEntity brand) {
        return new BrandSummary(brand.getId(), brand.getName(), brand.getSlug(), brand.getDescription(), brand.getLogoUrl(),
                brand.isActive(), brand.getCreatedAt(), brand.getUpdatedAt());
    }

    private PagedResponse<BrandSummary> page(Page<BrandEntity> page) {
        return new PagedResponse<>(page.map(this::summary).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private int safePage(int page) { return Math.max(page, 0); }
    private int safeSize(int size) { return Math.min(Math.max(size, 1), 100); }
}