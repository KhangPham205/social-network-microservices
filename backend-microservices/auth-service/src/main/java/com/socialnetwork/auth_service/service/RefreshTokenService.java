package com.socialnetwork.auth_service.service;

import com.kt.social.auth.dto.RefreshTokenRequest;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.UserCredential;

import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByToken(String token);
    TokenResponse  refresh(RefreshTokenRequest request);
    RefreshToken createRefreshToken(UserCredential user);
}