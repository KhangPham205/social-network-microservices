package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.UserCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<RefreshToken> findByToken(String token);

  void deleteByUser(UserCredential user);
}
