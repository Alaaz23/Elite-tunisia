package tn.elitetunisia.partner;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GuaranteeRepository extends JpaRepository<GuaranteeEntity, UUID> {

    @Query("select count(g) from GuaranteeEntity g where g.vehicle.id = :vehicleId and g.status = :status"
            + " and g.startsAt < :end and g.endsAt > :start")
    long countOverlapping(UUID vehicleId, GuaranteeStatus status, Instant start, Instant end);

    @Query("select count(g) from GuaranteeEntity g where g.vehicle.id = :vehicleId and g.status = :status"
            + " and g.endsAt > :now")
    long countActiveEndingAfter(UUID vehicleId, GuaranteeStatus status, Instant now);

    @Query("select g from GuaranteeEntity g where g.vehicle.id = :vehicleId and g.status = :status"
            + " and g.startsAt <= :start and g.endsAt >= :end order by g.startsAt")
    List<GuaranteeEntity> findCovering(UUID vehicleId, GuaranteeStatus status, Instant start, Instant end);

    @Query("select g.vehicle.id from GuaranteeEntity g where g.id = :id")
    Optional<UUID> findVehicleIdById(UUID id);

    List<GuaranteeEntity> findByVehicleIdOrderByStartsAtDesc(UUID vehicleId);

    List<GuaranteeEntity> findByPartnerIdOrderByStartsAtDesc(UUID partnerId);
}
