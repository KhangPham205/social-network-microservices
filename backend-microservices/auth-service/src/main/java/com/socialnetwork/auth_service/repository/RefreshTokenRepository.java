package com.socialnetwork.auth_service.repository;

import com.socialnetwork.auth_service.model.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByToken(String token);

  /** Revokes every refresh token of a user. Must run inside a transaction. */
  @Modifying
  @Query("delete from RefreshToken r where r.user.id = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
}
