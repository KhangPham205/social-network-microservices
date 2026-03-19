package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.*;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.auth_service.service.PasswordResetService;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import constants.ApiConstants;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.AUTH)
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordResetService passwordResetService;

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.badRequest().body("Invalid token header");
    }

    String token = authHeader.substring(7);
    authService.logout(token);
    return ResponseEntity.ok("Logged out successfully");
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
    return ResponseEntity.ok(refreshTokenService.refresh(request));
  }

  @PostMapping("/sendVerifyEmail")
  public ResponseEntity<?> sendVerifyEmail(@RequestBody SendVerifyEmailRequest request) {
    try {
      authService.sendVerificationCode(request.getEmail());
      return ResponseEntity.ok(Map.of("message", "Verification code sent successfully"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/resendVerifyEmail")
  public ResponseEntity<?> resendVerifyEmail(@RequestBody SendVerifyEmailRequest request) {
    try {
      authService.resendVerificationCode(request.getEmail());
      return ResponseEntity.ok(Map.of("message", "Verification code resent successfully"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Map<String, String>> resetPassword(
      @RequestBody PasswordResetRequest request) {
    try {
      passwordResetService.sendResetCode(request);
      return ResponseEntity.ok(Map.of("message", "Password reset code sent (simulated)"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<?> verifyOtp(@RequestBody OtpVerificationRequest request) {
    boolean success = authService.verifyOtp(request);
    if (success) {
      return ResponseEntity.ok(Map.of("message", "OTP verified successfully"));
    } else {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
    }
  }
}
