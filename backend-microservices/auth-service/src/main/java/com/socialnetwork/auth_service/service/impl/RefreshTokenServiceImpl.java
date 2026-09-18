package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.RefreshTokenResponse;
import com.socialnetwork.auth_service.dto.TokenResponse;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.RefreshTokenRepository;
import com.socialnetwork.auth_service.security.AuthorityMapper;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import com.socialnetwork.common.exception.InvalidCredentialsException;
import com.socialnetwork.common.security.JwtProperties;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

  private final JwtProvider jwtProvider;
  private final JwtProperties jwtProperties;
  private final AuthorityMapper authorityMapper;
  private final RefreshTokenRepository refreshTokenRepository;

  @Override
  @Transactional
  public RefreshToken createRefreshToken(UserCredential user) {
    RefreshToken refreshToken =
        RefreshToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiryDate(Instant.now().plusMillis(jwtProperties.getRefresh().getExpiration()))
            .build();
    return refreshTokenRepository.save(refreshToken);
  }

  @Override
  @Transactional
  public RefreshTokenResponse refresh(String refreshToken) {
    RefreshToken current =
        refreshTokenRepository
            .findByToken(refreshToken)
            .orElseThrow(
                () -> new InvalidCredentialsException("Invalid refresh token. Please log in again."));

    if (current.isExpired()) {
      refreshTokenRepository.delete(current);
      throw new InvalidCredentialsException("Refresh token has expired. Please log in again.");
    }

    UserCredential user = current.getUser();
    try {
      AccountStatusGuard.assertCanAuthenticate(user);
    } catch (RuntimeException denied) {
      refreshTokenRepository.deleteAllByUserId(user.getId());
      throw denied;
    }

    // Rotation: the presented token is consumed and a new one is issued.
    refreshTokenRepository.delete(current);
    RefreshToken rotated = createRefreshToken(user);

    String accessToken =
        jwtProvider.generateToken(user.getId(), user.getUsername(), authorityMapper.authorities(user));

    return RefreshTokenResponse.builder()
        .message("Token refreshed successfully")
        .tokenResponse(new TokenResponse(accessToken, rotated.getToken()))
        .roles(authorityMapper.roleNames(user))
        .build();
  }

  @Override
  @Transactional
  public void revokeAll(Long userId) {
    int revoked = refreshTokenRepository.deleteAllByUserId(userId);
    if (revoked > 0) {
      log.info("Revoked {} refresh token(s) of user {}", revoked, userId);
    }
  }

  @Override
  @Transactional
  public void revokeAllByToken(String refreshToken) {
    refreshTokenRepository
        .findByToken(refreshToken)
        .ifPresent(token -> revokeAll(token.getUser().getId()));
  }
}
