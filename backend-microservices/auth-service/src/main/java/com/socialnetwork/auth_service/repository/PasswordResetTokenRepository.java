package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByEmail(String email);
}
