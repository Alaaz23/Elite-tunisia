package tn.elitetunisia.partner;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<PartnerEntity, UUID> {
    Optional<PartnerEntity> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
