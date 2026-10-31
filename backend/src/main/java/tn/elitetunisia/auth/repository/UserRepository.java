package tn.elitetunisia.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;


import tn.elitetunisia.auth.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Décompte frais (requête scalaire) : à appeler après avoir pris le verrou du rôle SUPER_ADMIN. */
    @Query("select count(u) from UserEntity u join u.roles r where r.roleKey = 'SUPER_ADMIN' and u.enabled = true")
    long countActiveSuperAdmins();

    @Query("select count(u) > 0 from UserEntity u join u.roles r where r.roleKey = 'SUPER_ADMIN' and u.enabled = true and u.id = :id")
    boolean isActiveSuperAdmin(@org.springframework.data.repository.query.Param("id") java.util.UUID id);
}