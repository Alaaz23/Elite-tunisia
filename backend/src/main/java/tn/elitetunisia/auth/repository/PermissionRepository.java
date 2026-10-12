package tn.elitetunisia.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.elitetunisia.auth.entity.PermissionEntity;

public interface PermissionRepository extends JpaRepository<PermissionEntity, String> { }