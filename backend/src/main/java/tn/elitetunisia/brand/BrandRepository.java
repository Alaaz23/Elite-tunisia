package tn.elitetunisia.brand;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<BrandEntity, UUID> {
    Page<BrandEntity> findAllByActiveTrue(Pageable pageable);
    Optional<BrandEntity> findByIdAndActiveTrue(UUID id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    boolean existsBySlug(String slug);
    boolean existsByLogoUrl(String logoUrl);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    Optional<BrandEntity> findBySlugAndActiveTrue(String slug);
}