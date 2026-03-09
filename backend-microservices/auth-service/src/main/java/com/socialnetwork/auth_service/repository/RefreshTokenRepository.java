package com.socialnetwork.auth_service.repository;

import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(UserCredential user);
}