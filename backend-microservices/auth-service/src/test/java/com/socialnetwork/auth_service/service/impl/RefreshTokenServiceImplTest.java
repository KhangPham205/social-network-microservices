package com.socialnetwork.auth_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.auth_service.dto.RefreshTokenResponse;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.RefreshTokenRepository;
import com.socialnetwork.auth_service.security.AuthorityMapper;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.InvalidCredentialsException;
import com.socialnetwork.common.security.JwtProperties;
import com.socialnetwork.common.vo.AccountStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Refresh must rotate the presented token and refuse accounts that may no longer authenticate. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

  private static final long REFRESH_TTL_MS = 604_800_000L;

  @Mock private JwtProvider jwtProvider;
  @Mock private AuthorityMapper authorityMapper;
  @Mock private RefreshTokenRepository refreshTokenRepository;

  private JwtProperties jwtProperties;
  private RefreshTokenServiceImpl service;

  @BeforeEach
  void setUp() {
    jwtProperties = new JwtProperties();
    jwtProperties.setSecret("test-only-jwt-secret-0123456789abcdef0123456789");
    jwtProperties.getRefresh().setExpiration(REFRESH_TTL_MS);
    service =
        new RefreshTokenServiceImpl(
            jwtProvider, jwtProperties, authorityMapper, refreshTokenRepository);
  }

  private static UserCredential user(AccountStatus status) {
    return UserCredential.builder()
        .id(7L)
        .username("alice")
        .status(status)
        .roles(Set.of(Role.builder().name("USER").permissions(Set.of()).build()))
        .build();
  }

  private static RefreshToken storedToken(UserCredential user, Instant expiry) {
    return RefreshToken.builder().id(1L).token("old-token").user(user).expiryDate(expiry).build();
  }

  @Test
  @DisplayName("refresh rotates: the old token is deleted and a different one is stored")
  void refreshRotatesTheToken() {
    UserCredential user = user(AccountStatus.ACTIVE);
    RefreshToken current = storedToken(user, Instant.now().plus(1, ChronoUnit.DAYS));
    when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(current));
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(authorityMapper.authorities(user)).thenReturn(List.of("ROLE_USER"));
    when(authorityMapper.roleNames(user)).thenReturn(List.of("USER"));
    when(jwtProvider.generateToken(7L, "alice", List.of("ROLE_USER"))).thenReturn("new-access");

    RefreshTokenResponse response = service.refresh("old-token");

    verify(refreshTokenRepository).delete(current);

    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(saved.capture());
    assertThat(saved.getValue().getToken()).isNotEqualTo("old-token");
    assertThat(saved.getValue().getUser()).isSameAs(user);
    assertThat(saved.getValue().getExpiryDate()).isAfter(Instant.now());

    assertThat(response.getTokenResponse().getAccessToken()).isEqualTo("new-access");
    assertThat(response.getTokenResponse().getRefreshToken())
        .isEqualTo(saved.getValue().getToken())
        .isNotEqualTo("old-token");
    assertThat(response.getRoles()).containsExactly("USER");
  }

  @Test
  @DisplayName("the refresh-token lifetime comes from jwt.refresh.expiration in MILLISECONDS")
  void refreshTokenLifetimeIsReadAsMilliseconds() {
    UserCredential user = user(AccountStatus.ACTIVE);
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Instant before = Instant.now();
    RefreshToken created = service.createRefreshToken(user);

    // 7 days, not 7 days of nanos/seconds: tolerate a few seconds of test execution drift.
    assertThat(created.getExpiryDate())
        .isBetween(
            before.plusMillis(REFRESH_TTL_MS).minusSeconds(30),
            before.plusMillis(REFRESH_TTL_MS).plusSeconds(30));
  }

  @Test
  @DisplayName("an expired refresh token is deleted and rejected with 401")
  void expiredRefreshTokenIsRejected() {
    UserCredential user = user(AccountStatus.ACTIVE);
    RefreshToken current = storedToken(user, Instant.now().minusSeconds(1));
    when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(current));

    assertThatThrownBy(() -> service.refresh("old-token"))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(refreshTokenRepository).delete(current);
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("an unknown refresh token is rejected with 401")
  void unknownRefreshTokenIsRejected() {
    when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.refresh("old-token"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  @DisplayName("refreshing a BLOCKED account is refused and drops all of its tokens")
  void blockedAccountCannotRefresh() {
    UserCredential user = user(AccountStatus.BLOCKED);
    RefreshToken current = storedToken(user, Instant.now().plus(1, ChronoUnit.DAYS));
    when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(current));

    assertThatThrownBy(() -> service.refresh("old-token"))
        .isInstanceOf(AccessDeniedException.class);

    verify(refreshTokenRepository).deleteAllByUserId(7L);
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("revokeAllByToken resolves the owner and drops every one of its tokens")
  void revokeAllByTokenRevokesTheWholeSession() {
    UserCredential user = user(AccountStatus.ACTIVE);
    when(refreshTokenRepository.findByToken("old-token"))
        .thenReturn(Optional.of(storedToken(user, Instant.now().plusSeconds(60))));
    when(refreshTokenRepository.deleteAllByUserId(7L)).thenReturn(2);

    service.revokeAllByToken("old-token");

    verify(refreshTokenRepository).deleteAllByUserId(7L);
  }

  @Test
  @DisplayName("revoking an unknown token is a no-op")
  void revokeAllByUnknownTokenIsNoOp() {
    when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

    service.revokeAllByToken("ghost");

    verify(refreshTokenRepository, never()).deleteAllByUserId(any());
  }
}
