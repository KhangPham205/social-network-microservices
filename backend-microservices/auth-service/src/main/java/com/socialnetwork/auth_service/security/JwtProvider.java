package com.socialnetwork.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

  @Value("${jwt.secret}")
  private String jwtSecret;

  @Value("${jwt.expiration}")
  private long jwtExpirationMs;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(UserDetails userDetails, Long userId) {
    List<String> authorities =
        userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claims(Map.of("userId", userId, "roles", authorities))
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
        .signWith(getSigningKey()) // Cú pháp mới của jjwt 0.12.x
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public Claims getClaims(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload(); // Cú pháp mới thay cho getBody()
  }

  public String extractUsername(String token) {
    return getClaims(token).getSubject();
  }

  public Long extractUserId(String token) {
    Object idObj = getClaims(token).get("userId");
    if (idObj instanceof Integer) return ((Integer) idObj).longValue();
    if (idObj instanceof Long) return (Long) idObj;
    return null;
  }

  public List<String> extractRoles(String token) {
    List<String> rawRoles = getClaims(token).get("roles", List.class);

    if (rawRoles == null || rawRoles.isEmpty()) {
      return Collections.emptyList();
    }

    return rawRoles.stream()
        .filter(r -> r != null && r.startsWith("ROLE_"))
        .map(r -> r.substring(5))
        .toList();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
  }

  private boolean isTokenExpired(String token) {
    Date expiration = getClaims(token).getExpiration();
    return expiration.before(new Date());
  }
}
