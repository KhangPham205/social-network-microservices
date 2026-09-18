package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.RefreshTokenResponse;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.UserCredential;

public interface RefreshTokenService {

  RefreshToken createRefreshToken(UserCredential user);

  /** Validates and rotates the refresh token, issuing a new access token. */
  RefreshTokenResponse refresh(String refreshToken);

  /** Revokes every refresh token of the user (logout, block, password reset). */
  void revokeAll(Long userId);

  /** Revokes every refresh token of the user owning this token; no-op when unknown. */
  void revokeAllByToken(String refreshToken);
}
