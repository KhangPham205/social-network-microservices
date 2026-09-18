package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.*;
import com.socialnetwork.auth_service.security.JwtProvider;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.auth_service.service.PasswordResetService;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import com.socialnetwork.common.constants.ApiConstants;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiConstants.AUTH)
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordResetService passwordResetService;
  private final JwtProvider jwtProvider;

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);

    ResponseCookie jwtCookie =
        ResponseCookie.from("jwt", response.getToken().getAccessToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(7 * 24 * 60 * 60) // 7 days
            .sameSite("None")
            .build();

    ResponseCookie refreshCookie =
        ResponseCookie.from("refreshToken", response.getToken().getRefreshToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(30 * 24 * 60 * 60) // 30 days
            .sameSite("None")
            .build();

    //    response.setToken(null);

    return ResponseEntity.ok()
        .headers(
            headers -> {
              headers.add(HttpHeaders.SET_COOKIE, jwtCookie.toString());
              headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            })
        .body(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(@CookieValue(name = "jwt", required = false) String jwt) {

    if (jwt != null && !jwt.isEmpty()) {
      authService.logout(jwt);
    }

    ResponseCookie cleanJwtCookie =
        ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(0)
            .build();

    ResponseCookie cleanRefreshCookie =
        ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(0)
            .build();

    return ResponseEntity.ok()
        .headers(
            headers -> {
              headers.add(HttpHeaders.SET_COOKIE, cleanJwtCookie.toString());
              headers.add(HttpHeaders.SET_COOKIE, cleanRefreshCookie.toString());
            })
        .body("Logged out successfully");
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = "refreshToken", required = false) String refreshToken) {
    if (refreshToken == null || refreshToken.isEmpty()) {
      return ResponseEntity.status(401).body("Missing refresh token cookie");
    }

    TokenResponse newTokens = refreshTokenService.refresh(new RefreshTokenRequest(refreshToken));

    ResponseCookie newJwtCookie =
        ResponseCookie.from("jwt", newTokens.getAccessToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(24 * 60 * 60)
            .sameSite("None")
            .build();

    ResponseCookie newRefreshCookie =
        ResponseCookie.from("refreshToken", newTokens.getRefreshToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(7 * 24 * 60 * 60)
            .sameSite("None")
            .build();

    List<String> userRole = jwtProvider.extractRoles(newTokens.getAccessToken());

    return ResponseEntity.ok()
        .headers(
            headers -> {
              headers.add(HttpHeaders.SET_COOKIE, newJwtCookie.toString());
              headers.add(HttpHeaders.SET_COOKIE, newRefreshCookie.toString());
            })
        .body(new RefreshTokenResponse("Token refreshed successfully", newTokens, userRole));
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
