package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.UserCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByToken(String token);

  @Modifying
  @Transactional
  void deleteByUser(UserCredential user);
}
