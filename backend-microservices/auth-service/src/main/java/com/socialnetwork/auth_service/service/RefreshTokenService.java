package com.socialnetwork.auth_service.service;

import com.socialnetwork.auth_service.dto.RefreshTokenRequest;
import com.socialnetwork.auth_service.dto.TokenResponse;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.UserCredential;
import java.util.Optional;

public interface RefreshTokenService {
  Optional<RefreshToken> findByToken(String token);

  TokenResponse refresh(RefreshTokenRequest request);

  RefreshToken createRefreshToken(UserCredential user);
}
