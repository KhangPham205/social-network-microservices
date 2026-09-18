package com.socialnetwork.auth_service.security;

import static com.socialnetwork.common.constants.SecurityConstants.JWT_COOKIE;
import static com.socialnetwork.common.constants.SecurityConstants.REFRESH_TOKEN_COOKIE;

import com.socialnetwork.common.security.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code jwt} and {@code refreshToken} cookies (HttpOnly, Secure, SameSite=None, path /)
 * with a max-age equal to the corresponding token lifetime.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

  private static final String SAME_SITE = "None";

  private final JwtProperties jwtProperties;

  public ResponseCookie accessToken(String token) {
    return build(JWT_COOKIE, token, Duration.ofMillis(jwtProperties.getExpiration()));
  }

  public ResponseCookie refreshToken(String token) {
    return build(
        REFRESH_TOKEN_COOKIE, token, Duration.ofMillis(jwtProperties.getRefresh().getExpiration()));
  }

  public ResponseCookie clearAccessToken() {
    return build(JWT_COOKIE, "", Duration.ZERO);
  }

  public ResponseCookie clearRefreshToken() {
    return build(REFRESH_TOKEN_COOKIE, "", Duration.ZERO);
  }

  /** Adds both auth cookies to the response headers. */
  public void write(HttpHeaders headers, ResponseCookie... cookies) {
    for (ResponseCookie cookie : cookies) {
      headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
    }
  }

  private static ResponseCookie build(String name, String value, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .sameSite(SAME_SITE)
        .maxAge(maxAge)
        .build();
  }
}
