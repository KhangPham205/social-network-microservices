package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.RefreshTokenRequest;
import com.socialnetwork.auth_service.dto.TokenResponse;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.RefreshTokenRepository;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import exception.BadRequestException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

  private final JwtProvider jwtProvider;
  private final RefreshTokenRepository refreshTokenRepository;

  @Value("${jwt.refresh.expiration}")
  private Long refreshExpiration;

  @Override
  public Optional<RefreshToken> findByToken(String token) {
    return refreshTokenRepository.findByToken(token);
  }

  @Override
  public TokenResponse refresh(RefreshTokenRequest request) {
    String refreshToken = request.getRefreshToken();

    RefreshToken token =
        refreshTokenRepository
            .findByToken(refreshToken)
            .orElseThrow(
                () -> new BadRequestException("Invalid refresh token. Please log in again."));

    if (token.getExpiryDate().isBefore(Instant.now())) {
      refreshTokenRepository.delete(token);
      throw new BadRequestException("Invalid or expired code");
    }

    UserCredential userCredential = token.getUser();

    Set<Role> roles = userCredential.getRoles();
    String[] roleNames = roles.stream().map(Role::getName).toArray(String[]::new);

    UserDetails userDetails =
        User.builder()
            .username(userCredential.getUsername())
            .password(userCredential.getPassword())
            .roles(roleNames)
            .build();

    // Get userId from userCredential
    Long realUserId = userCredential.getId();

    String newAccessToken = jwtProvider.generateToken(userDetails, realUserId);

    return TokenResponse.builder().accessToken(newAccessToken).refreshToken(refreshToken).build();
  }

  @Override
  public RefreshToken createRefreshToken(UserCredential user) {
    RefreshToken refreshToken =
        RefreshToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())
            .expiryDate(Instant.now().plusSeconds(refreshExpiration))
            .build();
    return refreshTokenRepository.save(refreshToken);
  }
}
