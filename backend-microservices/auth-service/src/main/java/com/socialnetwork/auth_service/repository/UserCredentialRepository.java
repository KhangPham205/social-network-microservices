package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.UserCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

  Optional<UserCredential> findByUsername(String username);

  Optional<UserCredential> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);
}
