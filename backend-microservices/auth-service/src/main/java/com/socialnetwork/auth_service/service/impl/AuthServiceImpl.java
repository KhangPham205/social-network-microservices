package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.*;
import com.socialnetwork.auth_service.enums.AccountStatus;
import com.socialnetwork.auth_service.enums.OtpType;
import com.socialnetwork.auth_service.model.PasswordResetToken;
import com.socialnetwork.auth_service.model.RefreshToken;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.PasswordResetTokenRepository;
import com.socialnetwork.auth_service.repository.RefreshTokenRepository;
import com.socialnetwork.auth_service.repository.RoleRepository;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.auth_service.service.EmailService;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import events.UserCreatedEvent;
import exception.BadRequestException;
import exception.InvalidCredentialsException;
import exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

  private final JwtProvider jwtProvider;
  private final PasswordEncoder passwordEncoder;
  private final RoleRepository roleRepository;
  private final UserCredentialRepository userCredentialRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final EmailService emailService;

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  @Transactional
  public RegisterResponse register(RegisterRequest registerRequest) {
    if (userCredentialRepository.existsByUsername(registerRequest.getUsername())) {
      throw new BadRequestException("Username is already in use");
    }

    Role userRole =
        roleRepository
            .findByName("USER")
            .orElseThrow(() -> new IllegalStateException("Role 'USER' not found."));

    UserCredential userCredential =
        UserCredential.builder()
            .username(registerRequest.getUsername())
            .password(passwordEncoder.encode(registerRequest.getPassword()))
            .email(registerRequest.getEmail())
            .roles(Set.of(userRole))
            .status(AccountStatus.WAITING)
            .build();

    // 1. Lưu User vào DB nội bộ của Auth
    userCredentialRepository.save(userCredential);

    // 2. Bắn sự kiện (Event) ra Kafka thay vì gọi HTTP
    UserCreatedEvent event =
        new UserCreatedEvent(
            userCredential.getId(), userCredential.getUsername(), userCredential.getEmail());

    kafkaTemplate.send("user-created-topic", event);
    log.info("Đã bắn event UserCreatedEvent cho accountId: {}", userCredential.getId());

    // 3. Trả về ngay lập tức cho Client (Không phải đợi User Service)
    return new RegisterResponse("Registration initiated. Processing in background.");
  }

  @Override
  public LoginResponse login(LoginRequest loginRequest) {
    UserCredential userCredential =
        userCredentialRepository
            .findByUsername(loginRequest.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("Wrong username or password"));

    if (!passwordEncoder.matches(loginRequest.getPassword(), userCredential.getPassword())) {
      throw new InvalidCredentialsException("Sai tên đăng nhập hoặc mật khẩu");
    }

    //        if (userCredential.getStatus() != AccountStatus.ACTIVE) {
    //            return LoginResponse.builder()
    //                    .email(userCredential.getEmail())
    //                    .status(userCredential.getStatus())
    //                    .build();
    //        }

    Long idForToken = userCredential.getId();
    UserDetails userDetails = buildUserDetails(userCredential);
    String accessToken = jwtProvider.generateToken(userDetails, idForToken);
    RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

    return LoginResponse.builder()
        .id(userCredential.getId().toString())
        .email(userCredential.getEmail())
        .status(userCredential.getStatus())
        .roles(userCredential.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
        .token(
            TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build())
        .build();
  }

  @Override
  public void logout(String accessToken) {
    String username = jwtProvider.extractUsername(accessToken);
    userCredentialRepository
        .findByUsername(username)
        .ifPresent(refreshTokenRepository::deleteByUser);
    SecurityContextHolder.clearContext();
  }

  @Override
  public void sendVerificationCode(String email) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Email not found: " + email));

    String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
    user.setVerificationCode(code);
    user.setVerificationCodeExpiry(Instant.now().plusSeconds(300));
    user.setStatus(AccountStatus.PENDING);
    userCredentialRepository.save(user);

    emailService.sendEmail(user, OtpType.VERIFY_EMAIL, code);
  }

  @Override
  public void resendVerificationCode(String email) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Email not found: " + email));

    String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
    user.setVerificationCode(code);
    user.setVerificationCodeExpiry(Instant.now().plusSeconds(300));
    userCredentialRepository.save(user);

    emailService.sendEmail(user, OtpType.VERIFY_EMAIL, code);
  }

  @Override
  @Transactional
  public boolean verifyOtp(OtpVerificationRequest request) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(request.getEmail())
            .orElseThrow(
                () -> new ResourceNotFoundException("Email not found: " + request.getEmail()));

    if (request.getType() == OtpType.VERIFY_EMAIL) {
      return handleEmailVerification(user, request.getCode());
    } else if (request.getType() == OtpType.RESET_PASSWORD) {
      return handlePasswordReset(user, request.getCode());
    } else {
      throw new IllegalArgumentException("Invalid verification type");
    }
  }

  @Override
  @Transactional
  public RegisterResponse createStaffAccount(CreateStaffRequest request) {
    if (userCredentialRepository.existsByUsername(request.getUsername())) {
      throw new BadRequestException("Username is already in use");
    }

    Role staffRole =
        roleRepository
            .findByName(request.getRoleName().toUpperCase())
            .orElseThrow(
                () -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

    Set<Role> initialRoles = new HashSet<>();
    initialRoles.add(staffRole);

    UserCredential userCredential =
        UserCredential.builder()
            .username(request.getUsername())
            .password(passwordEncoder.encode(request.getPassword()))
            .email(request.getEmail())
            .roles(initialRoles)
            .status(AccountStatus.ACTIVE)
            .build();

    userCredentialRepository.save(userCredential);

    return new RegisterResponse("Staff account created successfully");
  }

  // --------------------- Helper methods --------------------------
  private UserDetails buildUserDetails(UserCredential userCredential) {
    Set<Role> roles = userCredential.getRoles();
    Set<String> roleNames = new HashSet<>();
    Set<String> permissionNames = new HashSet<>();

    for (Role role : roles) {
      roleNames.add(role.getName().replace("ROLE_", ""));

      role.getPermissions().forEach(permission -> permissionNames.add(permission.getName()));
    }

    return User.withUsername(userCredential.getUsername())
        .password(userCredential.getPassword())
        .roles(roleNames.toArray(new String[0])) // Gán các Role
        .authorities(permissionNames.toArray(new String[0])) // Gán các Permission
        .build();
  }

  private boolean handleEmailVerification(UserCredential user, String code) {
    if (code.equals(user.getVerificationCode())
        && user.getVerificationCodeExpiry().isAfter(Instant.now())) {
      user.setStatus(AccountStatus.ACTIVE);
      user.setVerificationCode(null);
      user.setVerificationCodeExpiry(null);
      userCredentialRepository.save(user);

      return true;
    }
    return false;
  }

  private boolean handlePasswordReset(UserCredential user, String code) {
    PasswordResetToken token =
        passwordResetTokenRepository
            .findByEmailAndCode(user.getEmail(), code)
            .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired code"));

    if (token.getExpiryDate().isBefore(Instant.now())) {
      throw new IllegalStateException("Code expired");
    }

    user.setPassword(token.getNewPassword());
    passwordResetTokenRepository.delete(token);
    userCredentialRepository.save(user);
    return true;
  }
}
