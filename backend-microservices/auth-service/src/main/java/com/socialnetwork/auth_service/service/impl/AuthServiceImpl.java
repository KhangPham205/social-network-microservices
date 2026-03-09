package com.socialnetwork.auth_service.service.impl;

import com.kt.social.auth.dto.*;
import com.kt.social.auth.enums.AccountStatus;
import com.kt.social.auth.enums.OtpType;
import com.kt.social.auth.model.PasswordResetToken;
import com.kt.social.auth.model.RefreshToken;
import com.kt.social.auth.model.Role;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PasswordResetTokenRepository;
import com.kt.social.auth.repository.RefreshTokenRepository;
import com.kt.social.auth.repository.RoleRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.security.JwtProvider;
import com.kt.social.auth.service.AuthService;
import com.kt.social.auth.service.EmailService;
import com.kt.social.auth.service.RefreshTokenService;
import com.kt.social.common.exception.BadRequestException;
import com.kt.social.common.exception.InvalidCredentialsException;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.domain.audit.service.ActivityLogService;
import com.kt.social.domain.user.model.UserInfo;
import com.kt.social.domain.user.repository.UserInfoRepository;
import com.kt.social.domain.user.repository.UserRepository;
import com.kt.social.domain.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userCredentialRepository.existsByUsername(registerRequest.getUsername())) {
            throw new BadRequestException("Username is already in use");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role 'USER' not found. Please seed database."));

        Set<Role> initialRoles = new HashSet<>();
        initialRoles.add(userRole);

        UserCredential userCredential = UserCredential.builder()
                .username(registerRequest.getUsername())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .email(registerRequest.getEmail())
                .roles(initialRoles) // <-- Gán Set
                .status(AccountStatus.PENDING)
                .build();

        userCredentialRepository.save(userCredential);

        com.kt.social.domain.user.model.User user = com.kt.social.domain.user.model.User.builder()
                .displayName(registerRequest.getFullname())
                .credential(userCredential)
                .build();

        userRepository.save(user);

        UserInfo userInfo = UserInfo.builder()
                .bio("")
                .favorites("")
                .dateOfBirth(registerRequest.getDateOfBirth())
                .user(user)
                .build();

        userInfoRepository.save(userInfo);

        activityLogService.logActivity(
                user,                   // (Actor)
                "AUTH:REGISTER",        // (Action)
                "User",                 // (TargetType)
                user.getId(),           // (TargetId)
                Map.of("email", user.getCredential().getEmail()) // (Details)
        );

        return new RegisterResponse("Registered Successfully");
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        UserCredential userCredential = userCredentialRepository.findByUsername(loginRequest.getUsername())
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

        com.kt.social.domain.user.model.User user = userCredential.getUser();

        if (user == null) {
            throw new IllegalStateException("Tài khoản " + userCredential.getUsername() + " không có User profile liên kết. Không thể đăng nhập.");
        }

        activityLogService.logActivity(
                user,           // (Actor)
                "AUTH:LOGIN",   // (Action)
                null, null, null
        );

        Long idForToken = user.getId();
        UserDetails userDetails = buildUserDetails(userCredential);
        String accessToken = jwtProvider.generateToken(userDetails, idForToken);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userCredential);

        return LoginResponse.builder()
                .id(user.getId().toString())
                .email(userCredential.getEmail())
                .status(userCredential.getStatus())
                .roles(userCredential.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .token(TokenResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken.getToken())
                        .build())
                .build();
    }

    @Override
    public void logout(String accessToken) {
        String username = jwtProvider.extractUsername(accessToken);
        userCredentialRepository.findByUsername(username).ifPresent(userCredential -> {
            if (userCredential.getUser() != null) {
                activityLogService.logActivity(
                        userCredential.getUser(), // (Actor)
                        "AUTH:LOGOUT",            // (Action)
                        null, null, null
                );
            }
            refreshTokenRepository.deleteByUser(userCredential);
        });
        SecurityContextHolder.clearContext();
    }

    @Override
    public void sendVerificationCode(String email){
        UserCredential user = userCredentialRepository.findByEmail(email)
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
        UserCredential user = userCredentialRepository.findByEmail(email)
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
        UserCredential user = userCredentialRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Email not found: " + request.getEmail()));

        return switch (request.getType()) {
            case VERIFY_EMAIL -> handleEmailVerification(user, request.getCode());
            case RESET_PASSWORD -> handlePasswordReset(user, request.getCode());
            default -> throw new IllegalArgumentException("Invalid verification type");
        };
    }

    @Override
    @Transactional
    public RegisterResponse createStaffAccount(CreateStaffRequest request) {
        if (userCredentialRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already in use");
        }

        com.kt.social.domain.user.model.User adminActor = userService.getCurrentUser();

        Role staffRole = roleRepository.findByName(request.getRoleName().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

        Set<Role> initialRoles = new HashSet<>();
        initialRoles.add(staffRole);

        UserCredential userCredential = UserCredential.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .roles(initialRoles)
                .status(AccountStatus.ACTIVE)
                .build();

        userCredentialRepository.save(userCredential);

        com.kt.social.domain.user.model.User user = com.kt.social.domain.user.model.User.builder()
                .displayName(request.getFullname())
                .credential(userCredential)
                .build();

        userRepository.save(user);

        UserInfo userInfo = UserInfo.builder()
                .bio("Tài khoản nhân viên.")
                .favorites("")
                .user(user)
                .build();

        userInfoRepository.save(userInfo);

        activityLogService.logActivity(
                adminActor,     // (Actor là Admin)
                "USER:CREATE",  // (Action)
                "User",         // (TargetType)
                user.getId(), // (TargetId là user mới)
                Map.of("newStaffUsername", user.getCredential().getUsername(),
                        "newStaffRole", staffRole.getName())
        );

        return new RegisterResponse("Staff account created successfully");
    }

    // --------------------- Helper methods --------------------------
    private UserDetails buildUserDetails(UserCredential userCredential) {
        Set<Role> roles = userCredential.getRoles();
        Set<String> roleNames = new HashSet<>();
        Set<String> permissionNames = new HashSet<>();

        for (Role role : roles) {
            roleNames.add(role.getName().replace("ROLE_", ""));

            role.getPermissions().forEach(permission ->
                    permissionNames.add(permission.getName())
            );
        }

        return User.withUsername(userCredential.getUsername())
                .password(userCredential.getPassword())
                .roles(roleNames.toArray(new String[0])) // Gán các Role
                .authorities(permissionNames.toArray(new String[0])) // Gán các Permission
                .build();
    }

    private boolean handleEmailVerification(UserCredential user, String code) {
        if (code.equals(user.getVerificationCode()) && user.getVerificationCodeExpiry().isAfter(Instant.now())) {
            user.setStatus(AccountStatus.ACTIVE);
            user.setVerificationCode(null);
            user.setVerificationCodeExpiry(null);
            userCredentialRepository.save(user);

            if (user.getUser() != null) {
                activityLogService.logActivity(
                        user.getUser(),
                        "AUTH:VERIFY_EMAIL",
                        null, null, null
                );
            }

            return true;
        }
        return false;
    }

    private boolean handlePasswordReset(UserCredential user, String code) {
        PasswordResetToken token = passwordResetTokenRepository.findByEmailAndCode(user.getEmail(), code)
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