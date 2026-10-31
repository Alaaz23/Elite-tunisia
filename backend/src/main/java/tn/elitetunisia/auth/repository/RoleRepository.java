package tn.elitetunisia.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import tn.elitetunisia.auth.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {
    Optional<RoleEntity> findByRoleKey(String roleKey);

    /** Verrou d'exclusion mutuelle des opérations qui peuvent réduire le nombre de SUPER_ADMIN actifs. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoleEntity r where r.roleKey = 'SUPER_ADMIN'")
    Optional<RoleEntity> lockSuperAdminRole();
}