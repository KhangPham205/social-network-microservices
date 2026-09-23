package com.socialnetwork.auth_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.socialnetwork.auth_service.client.UserServiceClient;
import com.socialnetwork.auth_service.dto.LoginRequest;
import com.socialnetwork.auth_service.dto.LoginResponse;
import com.socialnetwork.auth_service.dto.RegisterRequest;
import com.socialnetwork.auth_service.event.UserCreatedEventPublisher;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.PasswordResetTokenRepository;
import com.socialnetwork.auth_service.repository.RoleRepository;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.auth_service.security.AuthorityMapper;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.auth_service.service.EmailService;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.InvalidCredentialsException;
import com.socialnetwork.common.vo.AccountStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock private JwtProvider jwtProvider;
  @Mock private AuthorityMapper authorityMapper;
  @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
  @Mock private RoleRepository roleRepository;
  @Mock private UserCredentialRepository userCredentialRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private EmailService emailService;
  @Mock private UserServiceClient userClient;
  @Mock private UserCreatedEventPublisher userCreatedEventPublisher;

  @InjectMocks private AuthServiceImpl authService;

  private static UserCredential credential(AccountStatus status) {
    return UserCredential.builder()
        .id(7L)
        .username("alice")
        .email("alice@example.com")
        .password("$2a$10$hash")
        .status(status)
        .roles(Set.of(Role.builder().id(1L).name("USER").permissions(Set.of()).build()))
        .build();
  }

  private static LoginRequest loginRequest() {
    LoginRequest request = new LoginRequest();
    request.setUsername("alice");
    request.setPassword("secret");
    return request;
  }

  // ------------------------------------------------------------------ login

  @Test
  @DisplayName("login of an ACTIVE account issues an access token and a refresh token")
  void loginIssuesTokensForActiveAccount() {
    UserCredential user = credential(AccountStatus.ACTIVE);
    when(userCredentialRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);
    when(authorityMapper.authorities(user)).thenReturn(List.of("ROLE_USER"));
    when(authorityMapper.roleNames(user)).thenReturn(List.of("USER"));
    when(jwtProvider.generateToken(7L, "alice", List.of("ROLE_USER"))).thenReturn("access-token");
    when(refreshTokenService.createRefreshToken(user))
        .thenReturn(RefreshToken.builder().token("refresh-token").build());

    LoginResponse response = authService.login(loginRequest());

    assertThat(response.getId()).isEqualTo("7");
    assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(response.getRoles()).containsExactly("USER");
    assertThat(response.getToken().getAccessToken()).isEqualTo("access-token");
    assertThat(response.getToken().getRefreshToken()).isEqualTo("refresh-token");
  }

  @ParameterizedTest
  @EnumSource(
      value = AccountStatus.class,
      names = {"WAITING", "PENDING", "BLOCKED", "NOT_AUTHORIZED", "NOT_SOLVED"})
  @DisplayName("login is refused with 403 for every status other than ACTIVE")
  void loginRejectsNonActiveAccount(AccountStatus status) {
    UserCredential user = credential(status);
    when(userCredentialRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);

    assertThatThrownBy(() -> authService.login(loginRequest()))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(jwtProvider);
    verify(refreshTokenService, never()).createRefreshToken(any());
  }

  @Test
  @DisplayName("a wrong password yields InvalidCredentialsException, not AccessDenied")
  void loginWithWrongPasswordIsUnauthorized() {
    UserCredential user = credential(AccountStatus.ACTIVE);
    when(userCredentialRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(false);

    assertThatThrownBy(() -> authService.login(loginRequest()))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(jwtProvider);
  }

  @Test
  @DisplayName("an unknown username is indistinguishable from a wrong password")
  void loginWithUnknownUsernameIsUnauthorized() {
    when(userCredentialRepository.findByUsername("alice")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(loginRequest()))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  // ------------------------------------------------------------------ register

  private static RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setUsername("alice");
    request.setEmail("alice@example.com");
    request.setPassword("secret");
    return request;
  }

  @Test
  @DisplayName("register saves a WAITING credential and publishes UserCreatedEvent after commit")
  void registerPublishesEventAfterCommit() {
    when(userCredentialRepository.existsByUsername("alice")).thenReturn(false);
    when(userCredentialRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(roleRepository.findByName("USER"))
        .thenReturn(Optional.of(Role.builder().id(1L).name("USER").build()));
    when(passwordEncoder.encode("secret")).thenReturn("$2a$10$hash");
    when(userCredentialRepository.save(any(UserCredential.class)))
        .thenReturn(credential(AccountStatus.WAITING));

    authService.register(registerRequest());

    ArgumentCaptor<UserCredential> saved = ArgumentCaptor.forClass(UserCredential.class);
    verify(userCredentialRepository).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.WAITING);
    assertThat(saved.getValue().getPassword()).isEqualTo("$2a$10$hash");

    verify(userCreatedEventPublisher)
        .publishAfterCommit(new UserCreatedEvent(7L, "alice", "alice@example.com"));
  }

  @Test
  @DisplayName("register rejects a taken username with 409 and publishes nothing")
  void registerRejectsDuplicateUsername() {
    when(userCredentialRepository.existsByUsername("alice")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Username");

    verifyNoInteractions(userCreatedEventPublisher);
  }

  @Test
  @DisplayName("register rejects a taken e-mail with 409")
  void registerRejectsDuplicateEmail() {
    when(userCredentialRepository.existsByUsername("alice")).thenReturn(false);
    when(userCredentialRepository.existsByEmail("alice@example.com")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(registerRequest()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("mail");

    verifyNoInteractions(userCreatedEventPublisher);
  }

  // ------------------------------------------------------------------ logout

  @Test
  @DisplayName("logout revokes the session even when the access token has already expired")
  void logoutWorksWithExpiredAccessToken() {
    when(jwtProvider.extractUserIdAllowingExpired("expired-token")).thenReturn(Optional.of(7L));

    authService.logout("expired-token", "refresh-token");

    verify(refreshTokenService).revokeAllByToken("refresh-token");
    verify(refreshTokenService).revokeAll(7L);
  }

  @Test
  @DisplayName("logout without any token is a no-op instead of an error")
  void logoutWithoutTokensIsNoOp() {
    when(jwtProvider.extractUserIdAllowingExpired(null)).thenReturn(Optional.empty());

    authService.logout(null, null);

    verify(refreshTokenService, never()).revokeAllByToken(anyString());
    verify(refreshTokenService, never()).revokeAll(anyLong());
  }

  // ------------------------------------------------------------------ moderation

  @Test
  @DisplayName("a BLOCKED account loses every refresh token")
  void blockingRevokesRefreshTokens() {
    UserCredential user = credential(AccountStatus.ACTIVE);
    when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

    authService.updateRoleAndStatus(7L, null, AccountStatus.BLOCKED);

    assertThat(user.getStatus()).isEqualTo(AccountStatus.BLOCKED);
    verify(userCredentialRepository).save(user);
    verify(refreshTokenService).revokeAll(7L);
  }

  @Test
  @DisplayName("re-activating an account keeps the existing sessions")
  void activatingKeepsRefreshTokens() {
    UserCredential user = credential(AccountStatus.BLOCKED);
    when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

    authService.updateRoleAndStatus(7L, null, AccountStatus.ACTIVE);

    assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    verify(refreshTokenService, never()).revokeAll(anyLong());
  }

  // ------------------------------------------------------------------ internal API

  @Test
  @DisplayName("the internal credential view exposes id, username, e-mail, status and role names")
  void mapsCredentialsForInternalApi() {
    when(userCredentialRepository.findAllById(List.of(7L)))
        .thenReturn(List.of(credential(AccountStatus.ACTIVE)));

    assertThat(authService.getCredentialsByIds(List.of(7L)))
        .singleElement()
        .satisfies(
            dto -> {
              assertThat(dto.getId()).isEqualTo(7L);
              assertThat(dto.getUsername()).isEqualTo("alice");
              assertThat(dto.getEmail()).isEqualTo("alice@example.com");
              assertThat(dto.getStatus()).isEqualTo(AccountStatus.ACTIVE);
              assertThat(dto.getRoles()).containsExactly("USER");
            });
  }

  // ------------------------------------------------------------------ verification codes

  @Test
  @DisplayName("sendVerificationCode stores a fresh code, resets attempts and mails it")
  void sendVerificationCodeResetsAttempts() {
    UserCredential user = credential(AccountStatus.PENDING);
    user.setVerificationAttempts(3);
    when(userCredentialRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

    authService.sendVerificationCode("alice@example.com");

    assertThat(user.getVerificationCode()).hasSize(6);
    assertThat(user.getVerificationCodeExpiry()).isAfter(Instant.now());
    assertThat(user.getVerificationAttempts()).isZero();
    verify(emailService)
        .sendOtp(
            "alice@example.com",
            com.socialnetwork.auth_service.enums.OtpType.VERIFY_EMAIL,
            user.getVerificationCode());
  }

  @Test
  @DisplayName("an already verified account cannot ask for another verification code")
  void sendVerificationCodeRejectsActiveAccount() {
    when(userCredentialRepository.findByEmail("alice@example.com"))
        .thenReturn(Optional.of(credential(AccountStatus.ACTIVE)));

    assertThatThrownBy(() -> authService.sendVerificationCode("alice@example.com"))
        .isInstanceOf(ConflictException.class);

    verifyNoInteractions(emailService);
  }

  @Test
  @DisplayName("unused mocks stay unused: batch lookup never touches the mailer")
  void batchLookupDoesNotMail() {
    when(userCredentialRepository.findAllById(anyList())).thenReturn(List.of());

    assertThat(authService.getCredentialsByIds(List.of(1L))).isEmpty();
    verifyNoInteractions(emailService, userClient, passwordResetTokenRepository);
  }
}
