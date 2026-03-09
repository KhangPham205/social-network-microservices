package com.socialnetwork.auth_service.service.impl;

import com.kt.social.auth.dto.PasswordResetRequest;
import com.kt.social.auth.enums.OtpType;
import com.kt.social.auth.model.PasswordResetToken;
import com.kt.social.auth.model.UserCredential;
import com.kt.social.auth.repository.PasswordResetTokenRepository;
import com.kt.social.auth.repository.UserCredentialRepository;
import com.kt.social.auth.service.EmailService;
import com.kt.social.auth.service.PasswordResetService;
import com.kt.social.common.exception.ResourceNotFoundException;
import com.kt.social.domain.audit.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    private static final long EXPIRATION_TIME = 2 * 60; // 2 minutes

    @Override
    public void sendResetCode(PasswordResetRequest request) {
        UserCredential user = userCredentialRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        // Xóa mã cũ nếu tồn tại
        passwordResetTokenRepository.deleteByEmail(request.getEmail());

        String code = String.format("%06d", new SecureRandom().nextInt(999999)); // ví dụ 6 chữ số OTP

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(request.getEmail())
                .code(code)
                .newPassword(passwordEncoder.encode(request.getNewPassword()))
                .expiryDate(Instant.now().plusSeconds(EXPIRATION_TIME))
                .build();

        passwordResetTokenRepository.save(resetToken);

        if (user.getUser() != null) {
            activityLogService.logActivity(
                    user.getUser(),
                    "AUTH:RESET_PASSWORD_REQUEST",
                    null, null,
                    Map.of("email", request.getEmail())
            );
        }

        // Gửi email chứa mã xác thực
        emailService.sendEmail(user, OtpType.RESET_PASSWORD, code);
    }
}
