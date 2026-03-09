package com.socialnetwork.auth_service.service.impl;

import com.kt.social.auth.dto.RefreshTokenRequest;
import com.kt.social.auth.dto.LoginResponse;
import com.kt.social.auth.dto.TokenResponse;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.RefreshTokenRepository;
import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.service.RefreshTokenService;
import com.kt.social.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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

        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token. Please log in again."));

        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new BadRequestException("Invalid or expired code");
        }

        var userCredential = token.getUser();

        Set<Role> roles = userCredential.getRoles();
        String[] roleNames = roles.stream()
                .map(Role::getName)
                .toArray(String[]::new);

        UserDetails userDetails = User.builder()
                .username(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(roleNames)
                .build();

        com.kt.social.domain.user.model.User userProfile = userCredential.getUser();

        // 2. Kiểm tra
        if (userProfile == null) {
            throw new IllegalStateException("Tài khoản " + userCredential.getUsername() + " không có User profile liên kết.");
        }

        Long realUserId = userProfile.getId();

        String newAccessToken = jwtProvider.generateToken(userDetails, realUserId);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public RefreshToken createRefreshToken(UserCredential user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusSeconds(refreshExpiration))
                .build();
        return refreshTokenRepository.save(refreshToken);
    }
}
