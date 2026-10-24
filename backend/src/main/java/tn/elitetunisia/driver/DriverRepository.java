package tn.elitetunisia.driver;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID> {
    List<DriverEntity> findByActiveTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DriverEntity d where d.id = :id")
    java.util.Optional<DriverEntity> findLockedById(@Param("id") UUID id);
}
