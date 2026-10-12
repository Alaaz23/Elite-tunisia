package tn.elitetunisia.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.elitetunisia.auth.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {
    Optional<RoleEntity> findByRoleKey(String roleKey);
}