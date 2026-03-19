package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
  Optional<PasswordResetToken> findByCode(String token);

  Optional<PasswordResetToken> findByEmailAndCode(String email, String code);

  void deleteByEmail(String email);
}
