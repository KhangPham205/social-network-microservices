package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

  boolean existsByName(String name);
}
