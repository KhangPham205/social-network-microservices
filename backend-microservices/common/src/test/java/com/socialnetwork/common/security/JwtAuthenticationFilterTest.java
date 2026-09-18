package com.socialnetwork.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long!!";
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtValidator(SECRET));

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static String jwt() {
    return Jwts.builder()
        .subject("bob")
        .claims(Map.of("userId", 7L, "roles", List.of("ROLE_USER")))
        .expiration(new Date(System.currentTimeMillis() + 60_000))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  @Test
  void bearerHeader_authenticates() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
    req.addHeader("Authorization", "Bearer " + jwt());

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getName()).isEqualTo("7");
    assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactly("ROLE_USER");
    assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(7L);
    assertThat(SecurityUtils.hasRole("USER")).isTrue();
    assertThat(SecurityUtils.getCurrentToken()).isPresent();
  }

  @Test
  void jwtCookie_authenticates() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
    req.setCookies(new Cookie("jwt", jwt()));

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
  }

  @Test
  void invalidToken_staysAnonymous() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
    req.addHeader("Authorization", "Bearer garbage");

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(SecurityUtils.findCurrentUserId()).isEmpty();
  }
}
