package com.socialnetwork.common.security;

import static com.socialnetwork.common.constants.SecurityConstants.BEARER_PREFIX;
import static com.socialnetwork.common.constants.SecurityConstants.JWT_COOKIE;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resource-server filter: reads the access token from {@code Authorization: Bearer ...} or the
 * {@code jwt} cookie, verifies it and populates the security context with principal name = userId
 * and the authorities carried by the {@code roles} claim. Invalid tokens leave the request
 * anonymous; the authorization rules decide what happens next. Never logs token material.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtValidator jwtValidator;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      String token = resolveToken(request);
      if (token != null) {
        jwtValidator
            .authenticate(token)
            .ifPresent(
                principal -> {
                  var authentication =
                      new UsernamePasswordAuthenticationToken(
                          principal.userId().toString(), null, principal.authorities());
                  authentication.setDetails(
                      new JwtAuthenticationDetails(request, token, principal.username()));
                  SecurityContextHolder.getContext().setAuthentication(authentication);
                });
      }
    }
    filterChain.doFilter(request, response);
  }

  /** Bearer header first, then the {@code jwt} cookie. */
  public static String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length()).trim();
    }
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (JWT_COOKIE.equals(cookie.getName()) && cookie.getValue() != null) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }
}
