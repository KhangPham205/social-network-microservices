package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.Permission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
  Optional<Permission> findByName(String name);

  boolean existsByName(String name);
}
