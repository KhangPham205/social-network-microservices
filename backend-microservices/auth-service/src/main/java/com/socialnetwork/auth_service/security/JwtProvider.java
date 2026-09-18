package com.socialnetwork.auth_service.security;

import static com.socialnetwork.common.constants.SecurityConstants.CLAIM_ROLES;
import static com.socialnetwork.common.constants.SecurityConstants.CLAIM_USER_ID;

import com.socialnetwork.common.security.JwtProperties;
import com.socialnetwork.common.security.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues HS256 access tokens: subject = username, claims {@code userId} (Long) and {@code roles}
 * (List of authorities). Resource servers verify them with the common {@link JwtValidator}.
 */
@Component
public class JwtProvider {

  private final SecretKey signingKey;
  private final long expirationMs;

  public JwtProvider(JwtProperties properties) {
    this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    this.expirationMs = properties.getExpiration();
  }

  public String generateToken(Long userId, String username, List<String> authorities) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .claim(CLAIM_USER_ID, userId)
        .claim(CLAIM_ROLES, authorities)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expirationMs)))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Reads the userId of a token whose signature is valid, even when it has already expired (used
   * by logout so an expired session can still be closed). Empty for tampered/malformed tokens.
   */
  public Optional<Long> extractUserIdAllowingExpired(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      return Optional.ofNullable(JwtValidator.userIdOf(claims));
    } catch (ExpiredJwtException e) {
      return Optional.ofNullable(JwtValidator.userIdOf(e.getClaims()));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
