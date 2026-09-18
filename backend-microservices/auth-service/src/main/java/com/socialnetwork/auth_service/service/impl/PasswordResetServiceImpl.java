package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.PasswordResetRequest;
import com.socialnetwork.auth_service.enums.OtpType;
import com.socialnetwork.auth_service.model.PasswordResetToken;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.PasswordResetTokenRepository;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.auth_service.service.EmailService;
import com.socialnetwork.auth_service.service.PasswordResetService;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

  static final Duration CODE_LIFETIME = Duration.ofMinutes(5);

  private final UserCredentialRepository userCredentialRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;

  @Override
  @Transactional
  public void sendResetCode(PasswordResetRequest request) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

    passwordResetTokenRepository
        .findByEmail(user.getEmail())
        .ifPresent(
            existing -> {
              if (!existing.isExpired()) {
                throw new ConflictException(
                    "A password reset is already pending for this e-mail. Use the code you"
                        + " received or wait until it expires.");
              }
              passwordResetTokenRepository.delete(existing);
              passwordResetTokenRepository.flush();
            });

    String code = OtpCodes.generate();
    PasswordResetToken resetToken =
        PasswordResetToken.builder()
            .email(user.getEmail())
            .code(code)
            .newPassword(passwordEncoder.encode(request.getNewPassword()))
            .expiryDate(Instant.now().plus(CODE_LIFETIME))
            .attempts(0)
            .build();
    passwordResetTokenRepository.save(resetToken);

    // Throws 503 and rolls the token back when SMTP is down.
    emailService.sendOtp(user.getEmail(), OtpType.RESET_PASSWORD, code);
    log.info("Password reset code issued for user {}", user.getId());
  }
}
