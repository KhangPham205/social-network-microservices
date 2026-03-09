package com.socialnetwork.auth_service.repository;

import com.kt.social.auth.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCredentialRepository extends JpaRepository<UserCredential, Long> {

    Optional<UserCredential> findByUsername(String username);
    Optional<UserCredential> findByEmail(String email);
    boolean existsByUsername(String username);
}
