package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.client.UserServiceClient;
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
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.InvalidCredentialsException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

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
  private final UserServiceClient userClient;

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
  @Transactional
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
    if (userCredentialRepository.existsByEmail(request.getEmail())) {
      throw new BadRequestException("Email is already in use");
    }

    Role staffRole =
        roleRepository
            .findByName(request.getRoleName().toUpperCase())
            .orElseThrow(
                () -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

    UserCredential userCredential =
        UserCredential.builder()
            .username(request.getUsername())
            .password(passwordEncoder.encode(request.getPassword()))
            .email(request.getEmail())
            .roles(Set.of(staffRole))
            .status(AccountStatus.ACTIVE)
            .build();

    UserCredential savedCredential = userCredentialRepository.save(userCredential);

    try {
      userClient.createEmptyProfile(savedCredential.getId(), request.getFullname());
    } catch (Exception e) {
      // Rollback nếu User Service lỗi
      throw new RuntimeException("Lỗi khi tạo profile bên User Service: " + e.getMessage());
    }

    // Bắn sự kiện ra Kafka cho ActivityLogService (Nếu bạn có Audit Service riêng)
    // kafkaTemplate.send("audit-topic", new ActivityLogEvent("USER:CREATE", "User",
    // savedCredential.getId(), ...));

    return new RegisterResponse("Staff account created successfully");
  }

  @Override
  public void updateRoleAndStatus(Long credentialId, Set<String> roleNames, AccountStatus status) {
    UserCredential credential =
        userCredentialRepository
            .findById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("Credential not found"));

    if (roleNames != null && !roleNames.isEmpty()) {
      Set<Role> newRoles =
          roleNames.stream()
              .map(
                  name ->
                      roleRepository
                          .findByName(name.toUpperCase())
                          .orElseThrow(() -> new BadRequestException("Role not found: " + name)))
              .collect(Collectors.toSet());
      credential.setRoles(newRoles);
    }

    if (status != null) {
      credential.setStatus(status);
    }
    userCredentialRepository.save(credential);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthCredentialDto getCredentialById(Long id) {
    UserCredential credential =
        userCredentialRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Credential not found for ID: " + id));

    return mapToCredentialDto(credential);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuthCredentialDto> getCredentialsByIds(List<Long> ids) {
    List<UserCredential> credentials = userCredentialRepository.findAllById(ids);

    return credentials.stream().map(this::mapToCredentialDto).toList();
  }

  // --------------------- Helper methods --------------------------
  private AuthCredentialDto mapToCredentialDto(UserCredential credential) {
    // Rút trích tên các Role từ Set<Role> của Entity
    Set<String> roleNames =
        credential.getRoles().stream()
            .map(Role::getName) // Lấy ra cái tên (VD: "USER", "ADMIN")
            .collect(Collectors.toSet());

    return AuthCredentialDto.builder()
        .id(credential.getId())
        .username(credential.getUsername())
        .email(credential.getEmail())
        .status(credential.getStatus())
        .roles(roleNames)
        .build();
  }

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
