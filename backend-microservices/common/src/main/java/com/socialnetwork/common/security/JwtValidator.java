package com.socialnetwork.common.security;

import static com.socialnetwork.common.constants.SecurityConstants.CLAIM_ROLES;
import static com.socialnetwork.common.constants.SecurityConstants.CLAIM_USER_ID;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Verifies HS256 access tokens issued by auth-service. Stateless and thread-safe; the parser is
 * built once.
 */
public class JwtValidator {

  private final JwtParser parser;

  public JwtValidator(String secret) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.parser = Jwts.parser().verifyWith(key).build();
  }

  /** Signature + expiry check; empty when the token is malformed, tampered or expired. */
  public Optional<Claims> parse(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(parser.parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Full identity, or empty when the token is invalid or carries no numeric userId claim. */
  public Optional<JwtPrincipal> authenticate(String token) {
    return parse(token)
        .flatMap(
            claims -> {
              Long userId = userIdOf(claims);
              if (userId == null) {
                return Optional.empty();
              }
              return Optional.of(new JwtPrincipal(userId, claims.getSubject(), authoritiesOf(claims)));
            });
  }

  public boolean validateToken(String token) {
    return parse(token).isPresent();
  }

  /** @return the userId claim or {@code null} when the token is invalid or has no such claim */
  public Long extractUserId(String token) {
    return parse(token).map(JwtValidator::userIdOf).orElse(null);
  }

  public static Long userIdOf(Claims claims) {
    Object raw = claims.get(CLAIM_USER_ID);
    if (raw instanceof Number n) {
      return n.longValue();
    }
    if (raw instanceof String s) {
      try {
        return Long.parseLong(s);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public static List<GrantedAuthority> authoritiesOf(Claims claims) {
    Object raw = claims.get(CLAIM_ROLES);
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    return ((List<Object>) list)
        .stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(s -> !s.isBlank())
        .map(s -> (GrantedAuthority) new SimpleGrantedAuthority(s))
        .toList();
  }
}
