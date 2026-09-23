package com.socialnetwork.auth_service.controller;

import com.socialnetwork.auth_service.dto.LoginRequest;
import com.socialnetwork.auth_service.dto.LoginResponse;
import com.socialnetwork.auth_service.dto.OtpVerificationRequest;
import com.socialnetwork.auth_service.dto.PasswordResetRequest;
import com.socialnetwork.auth_service.dto.RefreshTokenResponse;
import com.socialnetwork.auth_service.dto.RegisterRequest;
import com.socialnetwork.auth_service.dto.RegisterResponse;
import com.socialnetwork.auth_service.dto.SendVerifyEmailRequest;
import com.socialnetwork.auth_service.security.AuthCookieFactory;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.auth_service.service.PasswordResetService;
import com.socialnetwork.auth_service.service.RefreshTokenService;
import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.constants.SecurityConstants;
import com.socialnetwork.common.exception.InvalidCredentialsException;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous entry points of the authentication flow. Tokens are returned both in the body (for
 * native clients) and as HttpOnly cookies (for the browser); errors are rendered by the shared
 * {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping(ApiConstants.AUTH)
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;
  private final PasswordResetService passwordResetService;
  private final AuthCookieFactory cookieFactory;

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    return ResponseEntity.ok()
        .headers(
            headers ->
                cookieFactory.write(
                    headers,
                    cookieFactory.accessToken(response.getToken().getAccessToken()),
                    cookieFactory.refreshToken(response.getToken().getRefreshToken())))
        .body(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logout(
      @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) @Nullable
          String authorization,
      @CookieValue(name = SecurityConstants.JWT_COOKIE, required = false) @Nullable String jwt,
      @CookieValue(name = SecurityConstants.REFRESH_TOKEN_COOKIE, required = false) @Nullable
          String refreshToken) {

    authService.logout(bearerOrCookie(authorization, jwt), refreshToken);

    return ResponseEntity.ok()
        .headers(
            headers ->
                cookieFactory.write(
                    headers, cookieFactory.clearAccessToken(), cookieFactory.clearRefreshToken()))
        .body(Map.of("message", "Logged out successfully"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<RefreshTokenResponse> refresh(
      @CookieValue(name = SecurityConstants.REFRESH_TOKEN_COOKIE, required = false) @Nullable
          String refreshToken) {
    if (!StringUtils.hasText(refreshToken)) {
      throw new InvalidCredentialsException("Missing refresh token");
    }

    RefreshTokenResponse response = refreshTokenService.refresh(refreshToken);
    return ResponseEntity.ok()
        .headers(
            headers ->
                cookieFactory.write(
                    headers,
                    cookieFactory.accessToken(response.getTokenResponse().getAccessToken()),
                    cookieFactory.refreshToken(response.getTokenResponse().getRefreshToken())))
        .body(response);
  }

  @PostMapping("/sendVerifyEmail")
  public ResponseEntity<Map<String, String>> sendVerifyEmail(
      @Valid @RequestBody SendVerifyEmailRequest request) {
    authService.sendVerificationCode(request.getEmail());
    return ResponseEntity.ok(Map.of("message", "Verification code sent successfully"));
  }

  /** Same behaviour as {@code /sendVerifyEmail}: a new code replaces the outstanding one. */
  @PostMapping("/resendVerifyEmail")
  public ResponseEntity<Map<String, String>> resendVerifyEmail(
      @Valid @RequestBody SendVerifyEmailRequest request) {
    authService.sendVerificationCode(request.getEmail());
    return ResponseEntity.ok(Map.of("message", "Verification code resent successfully"));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Map<String, String>> resetPassword(
      @Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.sendResetCode(request);
    return ResponseEntity.ok(Map.of("message", "Password reset code sent"));
  }

  @PostMapping("/verify-otp")
  public ResponseEntity<Map<String, String>> verifyOtp(
      @Valid @RequestBody OtpVerificationRequest request) {
    if (authService.verifyOtp(request)) {
      return ResponseEntity.ok(Map.of("message", "OTP verified successfully"));
    }
    return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
  }

  /** Access token of the caller: {@code Authorization: Bearer ...} first, then the jwt cookie. */
  @Nullable
  private static String bearerOrCookie(@Nullable String authorization, @Nullable String jwtCookie) {
    if (authorization != null && authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
      return authorization.substring(SecurityConstants.BEARER_PREFIX.length()).trim();
    }
    return jwtCookie;
  }
}
