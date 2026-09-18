package com.socialnetwork.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class JwtValidatorTest {

  private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long!!";
  private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

  private final JwtValidator validator = new JwtValidator(SECRET);

  private static String token(Map<String, Object> claims, long ttlMs) {
    return Jwts.builder()
        .subject("alice")
        .claims(claims)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ttlMs))
        .signWith(KEY)
        .compact();
  }

  @Test
  void authenticate_extractsUserIdAndRoles() {
    String jwt = token(Map.of("userId", 42, "roles", List.of("ROLE_ADMIN", "POST:DELETE_ANY")), 60_000);

    JwtPrincipal principal = validator.authenticate(jwt).orElseThrow();

    assertThat(principal.userId()).isEqualTo(42L);
    assertThat(principal.username()).isEqualTo("alice");
    assertThat(principal.authorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactlyInAnyOrder("ROLE_ADMIN", "POST:DELETE_ANY");
  }

  @Test
  void authenticate_rejectsTokenWithoutUserId() {
    assertThat(validator.authenticate(token(Map.of("roles", List.of("ROLE_USER")), 60_000)))
        .isEmpty();
  }

  @Test
  void parse_rejectsExpiredToken() {
    assertThat(validator.parse(token(Map.of("userId", 1), -1_000))).isEmpty();
    assertThat(validator.validateToken(token(Map.of("userId", 1), -1_000))).isFalse();
  }

  @Test
  void parse_rejectsTokenSignedWithAnotherKey() {
    JwtValidator other = new JwtValidator("another-secret-that-is-also-32-bytes-long!!!");
    assertThat(other.parse(token(Map.of("userId", 1), 60_000))).isEmpty();
  }

  @Test
  void parse_handlesGarbage() {
    assertThat(validator.parse(null)).isEmpty();
    assertThat(validator.parse("")).isEmpty();
    assertThat(validator.parse("not.a.jwt")).isEmpty();
    assertThat(validator.extractUserId("not.a.jwt")).isNull();
  }
}
