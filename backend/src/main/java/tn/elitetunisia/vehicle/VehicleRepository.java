package tn.elitetunisia.vehicle;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VehicleRepository extends JpaRepository<VehicleEntity, UUID>, JpaSpecificationExecutor<VehicleEntity> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select vehicle from VehicleEntity vehicle where vehicle.id = :id")
    java.util.Optional<VehicleEntity> findLocked(UUID id);
    boolean existsByBrandId(UUID brandId);
    boolean existsByCategoryId(UUID categoryId);
    boolean existsByImagesUrl(String url);
}