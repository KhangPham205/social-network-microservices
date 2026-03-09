package com.socialnetwork.auth_service.repository;

import com.kt.social.auth.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByCode(String token);
    Optional<PasswordResetToken> findByEmailAndCode(String email, String code);
    void deleteByEmail(String email);
}