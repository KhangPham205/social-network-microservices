package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.client.UserServiceClient;
import com.socialnetwork.auth_service.dto.AuthCredentialDto;
import com.socialnetwork.auth_service.dto.CreateStaffRequest;
import com.socialnetwork.auth_service.dto.LoginRequest;
import com.socialnetwork.auth_service.dto.LoginResponse;
import com.socialnetwork.auth_service.dto.OtpVerificationRequest;
import com.socialnetwork.auth_service.dto.RegisterRequest;
import com.socialnetwork.auth_service.dto.RegisterResponse;
import com.socialnetwork.auth_service.dto.TokenResponse;
import com.socialnetwork.auth_service.enums.OtpType;
import com.socialnetwork.auth_service.event.UserCreatedEventPublisher;
import com.socialnetwork.auth_service.exception.OtpAttemptsExceededException;
import com.socialnetwork.auth_service.model.PasswordResetToken;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.PasswordResetTokenRepository;
import com.socialnetwork.auth_service.repository.RoleRepository;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.auth_service.security.AuthorityMapper;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.auth_service.service.EmailService;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.InvalidCredentialsException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.vo.AccountStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

  /** Lifetime of an e-mail verification code. */
  static final Duration OTP_LIFETIME = Duration.ofMinutes(5);

  private static final String DEFAULT_ROLE = "USER";
  private static final String BAD_CREDENTIALS = "Wrong username or password";

  private final JwtProvider jwtProvider;
  private final AuthorityMapper authorityMapper;
  private final PasswordEncoder passwordEncoder;
  private final RoleRepository roleRepository;
  private final UserCredentialRepository userCredentialRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final EmailService emailService;
  private final UserServiceClient userClient;
  private final UserCreatedEventPublisher userCreatedEventPublisher;

  // ------------------------------------------------------------------ registration

  @Override
  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    assertUsernameAndEmailFree(request.getUsername(), request.getEmail());

    Role userRole =
        roleRepository
            .findByName(DEFAULT_ROLE)
            .orElseThrow(
                () -> new IllegalStateException("Role '" + DEFAULT_ROLE + "' is not seeded"));

    UserCredential credential =
        userCredentialRepository.save(
            UserCredential.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .roles(Set.of(userRole))
                .status(AccountStatus.WAITING)
                .build());

    // Published only after this transaction commits, so user-service never sees a rolled-back id.
    userCreatedEventPublisher.publishAfterCommit(
        new UserCreatedEvent(credential.getId(), credential.getUsername(), credential.getEmail()));

    return new RegisterResponse("Registration initiated. Processing in background.");
  }

  @Override
  @Transactional
  public RegisterResponse createStaffAccount(CreateStaffRequest request) {
    assertUsernameAndEmailFree(request.getUsername(), request.getEmail());

    String roleName = request.getRoleName().toUpperCase(Locale.ROOT);
    Role staffRole =
        roleRepository
            .findByName(roleName)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));

    UserCredential credential =
        userCredentialRepository.save(
            UserCredential.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .roles(Set.of(staffRole))
                .status(AccountStatus.ACTIVE)
                .build());

    // Staff accounts skip the saga: the profile is created synchronously and a failure here rolls
    // the credential back with the transaction.
    String displayName =
        StringUtils.hasText(request.getFullname()) ? request.getFullname() : credential.getUsername();
    userClient.createEmptyProfile(credential.getId(), displayName);

    log.info("Staff account {} created with role {}", credential.getId(), roleName);
    return new RegisterResponse("Staff account created successfully");
  }

  // ------------------------------------------------------------------ session

  @Override
  @Transactional
  public LoginResponse login(LoginRequest request) {
    UserCredential credential =
        userCredentialRepository
            .findByUsername(request.getUsername())
            .orElseThrow(() -> new InvalidCredentialsException(BAD_CREDENTIALS));

    if (!passwordEncoder.matches(request.getPassword(), credential.getPassword())) {
      throw new InvalidCredentialsException(BAD_CREDENTIALS);
    }
    AccountStatusGuard.assertCanAuthenticate(credential);

    String accessToken =
        jwtProvider.generateToken(
            credential.getId(), credential.getUsername(), authorityMapper.authorities(credential));
    RefreshToken refreshToken = refreshTokenService.createRefreshToken(credential);

    return LoginResponse.builder()
        .id(credential.getId().toString())
        .email(credential.getEmail())
        .status(credential.getStatus())
        .roles(authorityMapper.roleNames(credential))
        .token(new TokenResponse(accessToken, refreshToken.getToken()))
        .build();
  }

  @Override
  @Transactional
  public void logout(@Nullable String accessToken, @Nullable String refreshToken) {
    if (StringUtils.hasText(refreshToken)) {
      refreshTokenService.revokeAllByToken(refreshToken);
    }
    // Works with an already expired access token: only the signature is checked.
    jwtProvider.extractUserIdAllowingExpired(accessToken).ifPresent(refreshTokenService::revokeAll);
    SecurityContextHolder.clearContext();
  }

  // ------------------------------------------------------------------ one-time codes

  @Override
  @Transactional
  public void sendVerificationCode(String email) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("No account found with this e-mail"));

    if (user.getStatus() == AccountStatus.ACTIVE) {
      throw new ConflictException("This account is already verified");
    }

    String code = OtpCodes.generate();
    user.setVerificationCode(code);
    user.setVerificationCodeExpiry(Instant.now().plus(OTP_LIFETIME));
    user.setVerificationAttempts(0);
    userCredentialRepository.save(user);

    // Throws 503 and rolls the code back when SMTP is down.
    emailService.sendOtp(user.getEmail(), OtpType.VERIFY_EMAIL, code);
  }

  @Override
  @Transactional(noRollbackFor = OtpAttemptsExceededException.class)
  public boolean verifyOtp(OtpVerificationRequest request) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("No account found with this e-mail"));

    return switch (request.getType()) {
      case VERIFY_EMAIL -> verifyEmail(user, request.getCode());
      case RESET_PASSWORD -> applyPasswordReset(user, request.getCode());
    };
  }

  // ------------------------------------------------------------------ internal API

  @Override
  @Transactional
  public void updateRoleAndStatus(
      Long credentialId, @Nullable Set<String> roleNames, @Nullable AccountStatus status) {
    UserCredential credential =
        userCredentialRepository
            .findById(credentialId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Credential not found: " + credentialId));

    if (roleNames != null && !roleNames.isEmpty()) {
      Set<Role> roles = new LinkedHashSet<>();
      for (String name : roleNames) {
        String normalised = name.toUpperCase(Locale.ROOT);
        roles.add(
            roleRepository
                .findByName(normalised)
                .orElseThrow(
                    () -> new BadRequestException("Role not found: " + normalised)));
      }
      credential.setRoles(roles);
    }

    if (status != null) {
      credential.setStatus(status);
    }
    userCredentialRepository.save(credential);

    // A user who lost the right to be signed in must not be able to refresh an old session.
    if (status != null && status != AccountStatus.ACTIVE) {
      refreshTokenService.revokeAll(credential.getId());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public AuthCredentialDto getCredentialById(Long id) {
    return userCredentialRepository
        .findById(id)
        .map(AuthServiceImpl::toDto)
        .orElseThrow(() -> new ResourceNotFoundException("Credential not found: " + id));
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuthCredentialDto> getCredentialsByIds(List<Long> ids) {
    return userCredentialRepository.findAllById(ids).stream().map(AuthServiceImpl::toDto).toList();
  }

  // ------------------------------------------------------------------ helpers

  private void assertUsernameAndEmailFree(String username, String email) {
    if (userCredentialRepository.existsByUsername(username)) {
      throw new ConflictException("Username is already in use");
    }
    if (userCredentialRepository.existsByEmail(email)) {
      throw new ConflictException("E-mail is already in use");
    }
  }

  private boolean verifyEmail(UserCredential user, String code) {
    if (user.getVerificationCode() == null || user.getVerificationCodeExpiry() == null) {
      throw new BadRequestException("No verification code was requested for this account");
    }
    if (user.getVerificationCodeExpiry().isBefore(Instant.now())) {
      clearVerificationCode(user);
      userCredentialRepository.save(user);
      return false;
    }
    if (!user.getVerificationCode().equals(code)) {
      int attempts = user.getVerificationAttempts() + 1;
      user.setVerificationAttempts(attempts);
      if (attempts >= OtpCodes.MAX_ATTEMPTS) {
        clearVerificationCode(user);
        userCredentialRepository.save(user);
        throw new OtpAttemptsExceededException(
            "Too many wrong codes. Please request a new verification code.");
      }
      userCredentialRepository.save(user);
      return false;
    }

    clearVerificationCode(user);
    user.setStatus(AccountStatus.ACTIVE);
    userCredentialRepository.save(user);
    log.info("Account {} verified its e-mail", user.getId());
    return true;
  }

  private boolean applyPasswordReset(UserCredential user, String code) {
    PasswordResetToken token =
        passwordResetTokenRepository
            .findByEmail(user.getEmail())
            .orElseThrow(
                () -> new BadRequestException("No password reset is pending for this e-mail"));

    if (token.isExpired()) {
      passwordResetTokenRepository.delete(token);
      return false;
    }
    if (!token.getCode().equals(code)) {
      int attempts = token.getAttempts() + 1;
      token.setAttempts(attempts);
      if (attempts >= OtpCodes.MAX_ATTEMPTS) {
        passwordResetTokenRepository.delete(token);
        throw new OtpAttemptsExceededException(
            "Too many wrong codes. Please request a new reset code.");
      }
      passwordResetTokenRepository.save(token);
      return false;
    }

    // The new password was already BCrypt-encoded when the reset was requested.
    user.setPassword(token.getNewPassword());
    userCredentialRepository.save(user);
    passwordResetTokenRepository.delete(token);
    refreshTokenService.revokeAll(user.getId());
    log.info("Password reset completed for account {}", user.getId());
    return true;
  }

  private static void clearVerificationCode(UserCredential user) {
    user.setVerificationCode(null);
    user.setVerificationCodeExpiry(null);
    user.setVerificationAttempts(0);
  }

  private static AuthCredentialDto toDto(UserCredential credential) {
    return AuthCredentialDto.builder()
        .id(credential.getId())
        .username(credential.getUsername())
        .email(credential.getEmail())
        .status(credential.getStatus())
        .roles(
            credential.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
        .build();
  }
}
