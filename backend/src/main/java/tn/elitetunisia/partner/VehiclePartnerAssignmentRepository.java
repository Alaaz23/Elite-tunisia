package tn.elitetunisia.partner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiclePartnerAssignmentRepository extends JpaRepository<VehiclePartnerAssignmentEntity, UUID> {
    Optional<VehiclePartnerAssignmentEntity> findFirstByVehicleIdAndEndedAtIsNull(UUID vehicleId);
    List<VehiclePartnerAssignmentEntity> findByVehicleIdOrderByAssignedAtDesc(UUID vehicleId);
    boolean existsByVehicleId(UUID vehicleId);
}
