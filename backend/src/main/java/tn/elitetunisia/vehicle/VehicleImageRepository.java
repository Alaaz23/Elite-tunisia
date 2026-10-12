package tn.elitetunisia.vehicle;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleImageRepository extends JpaRepository<VehicleImageEntity, UUID> {
    boolean existsByUrl(String url);
    Optional<VehicleImageEntity> findByIdAndVehicleId(UUID id, UUID vehicleId);
}