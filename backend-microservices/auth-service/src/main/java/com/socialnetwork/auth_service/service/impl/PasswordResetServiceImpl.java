package com.socialnetwork.auth_service.service.impl;

import com.socialnetwork.auth_service.dto.PasswordResetRequest;
import com.socialnetwork.auth_service.enums.OtpType;
import com.socialnetwork.auth_service.model.PasswordResetToken;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.PasswordResetTokenRepository;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.auth_service.service.EmailService;
import com.socialnetwork.auth_service.service.PasswordResetService;
import exception.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

  private final UserCredentialRepository userCredentialRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailService emailService;

  private static final long EXPIRATION_TIME = 2 * 60; // 2 minutes

  @Override
  public void sendResetCode(PasswordResetRequest request) {
    UserCredential user =
        userCredentialRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

    // Xóa mã cũ nếu tồn tại
    passwordResetTokenRepository.deleteByEmail(request.getEmail());

    String code = String.format("%06d", new SecureRandom().nextInt(999999)); // ví dụ 6 chữ số OTP

    PasswordResetToken resetToken =
        PasswordResetToken.builder()
            .email(request.getEmail())
            .code(code)
            .newPassword(passwordEncoder.encode(request.getNewPassword()))
            .expiryDate(Instant.now().plusSeconds(EXPIRATION_TIME))
            .build();

    passwordResetTokenRepository.save(resetToken);

    // Gửi email chứa mã xác thực
    emailService.sendEmail(user, OtpType.RESET_PASSWORD, code);
  }
}
