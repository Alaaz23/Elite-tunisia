package tn.elitetunisia.vehicle;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tn.elitetunisia.brand.BrandEntity;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.catalogue.CatalogueNotFoundException;
import tn.elitetunisia.catalogue.dto.BrandSummary;
import tn.elitetunisia.catalogue.dto.CategorySummary;
import tn.elitetunisia.catalogue.dto.InvalidCatalogueRequestException;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.catalogue.dto.VehicleImageSummary;
import tn.elitetunisia.catalogue.dto.VehicleSearchRequest;
import tn.elitetunisia.catalogue.dto.VehicleSummary;
import tn.elitetunisia.category.CategoryEntity;
import tn.elitetunisia.category.CategoryRepository;
import tn.elitetunisia.common.audit.AuditService;
import tn.elitetunisia.common.storage.FileStorageService;

@Service
public class VehicleService {
    private static final Logger logger = LoggerFactory.getLogger(VehicleService.class);
    private final VehicleRepository vehicles;
    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final AuditService audit;
    private final FileStorageService storage;

    public VehicleService(VehicleRepository vehicles, BrandRepository brands, CategoryRepository categories,
            AuditService audit, FileStorageService storage) {
        this.vehicles = vehicles;
        this.brands = brands;
        this.categories = categories;
        this.audit = audit;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PagedResponse<VehicleSummary> publicSearch(VehicleSearchRequest filter) {
        validateRange(filter);
        Page<VehicleEntity> page = vehicles.findAll(VehicleSpecification.matching(filter, true), pageable(filter));
        return response(page);
    }
    @Transactional(readOnly = true)
    public PagedResponse<VehicleSummary> availableSearch(VehicleSearchRequest filter, java.time.Instant start, java.time.Instant end) {
        validateRange(filter);
        return response(vehicles.findAll(VehicleSpecification.matching(filter, true)
                .and(VehicleSpecification.availableDuring(start, end)), pageable(filter)));
    }

    @Transactional(readOnly = true)
    public VehicleSummary publicGet(UUID id) {
        VehicleEntity vehicle = vehicles.findById(id).filter(this::isPublic).orElseThrow(CatalogueNotFoundException::new);
        return summary(vehicle);
    }

    @Transactional(readOnly = true)
    public PagedResponse<VehicleSummary> adminSearch(VehicleSearchRequest filter) {
        validateRange(filter);
        return response(vehicles.findAll(VehicleSpecification.matching(filter, false), pageable(filter)));
    }

    @Transactional(readOnly = true)
    public VehicleSummary adminGet(UUID id) { return summary(find(id)); }

    @Transactional
    public VehicleSummary create(VehicleUpsertRequest request, UUID actorId, String ipAddress) {
        validateImages(request.images());
        BrandEntity brand = brands.findById(request.brandId()).orElseThrow(CatalogueNotFoundException::new);
        CategoryEntity category = categories.findById(request.categoryId()).orElseThrow(CatalogueNotFoundException::new);
        VehicleEntity vehicle = new VehicleEntity(brand, category, request.model().trim(), request.year(), request.description().trim(),
                request.pricePerDay(), request.pricePerWeek(), request.priceWithDriver(), request.currency(), request.passengers(),
                request.doors(), request.transmission(), request.fuel(), request.withDriverAvailable(),
                request.status() == null ? VehicleStatus.AVAILABLE : request.status());
        vehicle.replaceFeatures(normalizeFeatures(request.features()));
        vehicle.replaceImages(request.images() == null ? List.of() : request.images());
        VehicleEntity saved = vehicles.save(vehicle);
        audit.recordAudit(actorId, "VEHICLE_CREATED", "VEHICLE", saved.getId(), ipAddress, "SUCCESS");
        return summary(saved);
    }

    @Transactional
    public VehicleSummary update(UUID id, VehicleUpsertRequest request, UUID actorId, String ipAddress) {
        validateImages(request.images());
        VehicleEntity vehicle = find(id);
        BrandEntity brand = brands.findById(request.brandId()).orElseThrow(CatalogueNotFoundException::new);
        CategoryEntity category = categories.findById(request.categoryId()).orElseThrow(CatalogueNotFoundException::new);
        List<String> retainedUrls = request.images() == null ? List.of() : request.images().stream().map(VehicleImageRequest::url).toList();
        List<String> replacedUrls = vehicle.getImages().stream().map(VehicleImageEntity::getUrl)
            .filter(url -> !retainedUrls.contains(url)).toList();
        vehicle.update(brand, category, request.model().trim(), request.year(), request.description().trim(),
                request.pricePerDay(), request.pricePerWeek(), request.priceWithDriver(), request.currency(), request.passengers(),
                request.doors(), request.transmission(), request.fuel(), request.withDriverAvailable());
        if (request.status() != null) vehicle.setStatus(request.status());
        vehicle.getFeatures().clear();
        vehicle.getImages().clear();
        vehicles.flush();
        vehicle.replaceFeatures(normalizeFeatures(request.features()));
        vehicle.replaceImages(request.images() == null ? List.of() : request.images());
        scheduleFileDeletion(replacedUrls);
        audit.recordAudit(actorId, "VEHICLE_UPDATED", "VEHICLE", id, ipAddress, "SUCCESS");
        return summary(vehicle);
    }

    @Transactional
    public VehicleSummary updateStatus(UUID id, VehicleStatusRequest request, UUID actorId, String ipAddress) {
        VehicleEntity vehicle = find(id);
        vehicle.setStatus(request.status());
        audit.recordAudit(actorId, "VEHICLE_STATUS_UPDATED", "VEHICLE", id, ipAddress, "SUCCESS");
        return summary(vehicle);
    }

    @Transactional
    public void delete(UUID id, UUID actorId, String ipAddress) {
        VehicleEntity vehicle = find(id);
        List<String> urls = vehicle.getImages().stream().map(VehicleImageEntity::getUrl).toList();
        vehicles.delete(vehicle);
        scheduleFileDeletion(urls);
        audit.recordAudit(actorId, "VEHICLE_DELETED", "VEHICLE", id, ipAddress, "SUCCESS");
    }

    private VehicleEntity find(UUID id) { return vehicles.findById(id).orElseThrow(CatalogueNotFoundException::new); }

    private boolean isPublic(VehicleEntity vehicle) {
        return vehicle.getStatus() == VehicleStatus.AVAILABLE && vehicle.getBrand().isActive() && vehicle.getCategory().isActive();
    }

    private void validateImages(List<VehicleImageRequest> images) {
        if (images != null && images.stream().filter(VehicleImageRequest::isPrimary).count() > 1) {
            throw new InvalidCatalogueRequestException("Une seule image principale est autorisée.");
        }
        if (images != null && images.stream().map(VehicleImageRequest::position).distinct().count() != images.size()) {
            throw new InvalidCatalogueRequestException("La position des images doit être unique.");
        }
    }

    private List<String> normalizeFeatures(List<String> features) {
        if (features == null) return List.of();
        return features.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private Pageable pageable(VehicleSearchRequest filter) {
        int page = filter.page() == null ? 0 : Math.max(filter.page(), 0);
        int size = filter.size() == null ? 12 : Math.min(Math.max(filter.size(), 1), 100);
        Sort sort = switch (filter.sort() == null ? "newest" : filter.sort().toLowerCase()) {
            case "price_asc" -> Sort.by("pricePerDay").ascending().and(Sort.by("id").ascending());
            case "price_desc" -> Sort.by("pricePerDay").descending().and(Sort.by("id").ascending());
            case "year_desc" -> Sort.by("year").descending().and(Sort.by("id").ascending());
            case "name_asc" -> Sort.by("model").ascending().and(Sort.by("id").ascending());
            case "newest" -> Sort.by("createdAt").descending().and(Sort.by("id").ascending());
            default -> throw new InvalidCatalogueRequestException("Critère de tri non pris en charge.");
        };
        return PageRequest.of(page, size, sort);
    }

    private void validateRange(VehicleSearchRequest filter) {
        if (filter.minPrice() != null && filter.minPrice().signum() < 0
                || filter.maxPrice() != null && filter.maxPrice().signum() < 0) {
            throw new InvalidCatalogueRequestException("Les filtres de prix ne peuvent pas être négatifs.");
        }
        if (filter.passengers() != null && (filter.passengers() < 1 || filter.passengers() > 100)) {
            throw new InvalidCatalogueRequestException("Le nombre de places doit être compris entre 1 et 100.");
        }
        if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new InvalidCatalogueRequestException("Le prix minimum ne peut pas dépasser le prix maximum.");
        }
    }

    private PagedResponse<VehicleSummary> response(Page<VehicleEntity> page) {
        return new PagedResponse<>(page.map(this::summary).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private VehicleSummary summary(VehicleEntity vehicle) {
        List<VehicleImageSummary> images = vehicle.getImages().stream()
                .sorted(Comparator.comparing(VehicleImageEntity::getPosition))
                .map(image -> new VehicleImageSummary(image.getId(), image.getUrl(), image.getAltText(),
                        image.getPosition(), image.isPrimary(), image.getCreatedAt())).toList();
        BrandEntity brand = vehicle.getBrand();
        CategoryEntity category = vehicle.getCategory();
        return new VehicleSummary(vehicle.getId(), new BrandSummary(brand.getId(), brand.getName(), brand.getSlug(),
                brand.getDescription(), brand.getLogoUrl(), brand.isActive(), brand.getCreatedAt(), brand.getUpdatedAt()),
                vehicle.getModel(), vehicle.getYear(), new CategorySummary(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.isActive(), category.getCreatedAt(), category.getUpdatedAt()),
                vehicle.getDescription(), vehicle.getPricePerDay(), vehicle.getPricePerWeek(), vehicle.getPriceWithDriver(),
                vehicle.getCurrency(), vehicle.getPassengers(), vehicle.getDoors(), vehicle.getTransmission(), vehicle.getFuel(),
                vehicle.isWithDriverAvailable(), vehicle.getStatus(), images,
                vehicle.getFeatures().stream().sorted(Comparator.comparing(VehicleFeatureEntity::getPosition))
                        .map(VehicleFeatureEntity::getName).toList(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }

    private void scheduleFileDeletion(List<String> urls) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String url : urls) {
                    if (vehicles.existsByImagesUrl(url) || brands.existsByLogoUrl(url)) continue;
                    try { storage.deleteUrl(url); }
                    catch (IOException exception) { logger.warn("Unable to delete replaced vehicle image {}", url, exception); }
                }
            }
        });
    }
}