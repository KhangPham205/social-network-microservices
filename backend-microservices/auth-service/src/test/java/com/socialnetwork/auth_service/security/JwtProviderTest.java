package com.socialnetwork.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialnetwork.common.security.JwtPrincipal;
import com.socialnetwork.common.security.JwtProperties;
import com.socialnetwork.common.security.JwtValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/** Tokens issued here must be accepted by the validator every resource server uses. */
class JwtProviderTest {

  private static final String SECRET = "test-only-jwt-secret-0123456789abcdef0123456789";

  private static JwtProperties properties(long expirationMs) {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(SECRET);
    properties.setExpiration(expirationMs);
    return properties;
  }

  @Test
  @DisplayName("round-trip: the common JwtValidator recovers userId, subject and authorities")
  void issuedTokenIsAcceptedByCommonValidator() {
    JwtProvider provider = new JwtProvider(properties(60_000L));
    List<String> authorities = List.of("ROLE_ADMIN", "ROLE_USER", "USER:CREATE");

    String token = provider.generateToken(42L, "alice", authorities);

    Optional<JwtPrincipal> principal = new JwtValidator(SECRET).authenticate(token);
    assertThat(principal).isPresent();
    assertThat(principal.get().userId()).isEqualTo(42L);
    assertThat(principal.get().username()).isEqualTo("alice");
    assertThat(principal.get().authorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyElementsOf(authorities);
  }

  @Test
  @DisplayName("a token signed with another secret is rejected")
  void tokenSignedWithAnotherSecretIsRejected() {
    String token = new JwtProvider(properties(60_000L)).generateToken(1L, "alice", List.of());

    assertThat(new JwtValidator("another-secret-0123456789abcdef0123456789").parse(token))
        .isEmpty();
  }

  @Test
  @DisplayName("an expired token fails validation but still yields its userId for logout")
  void expiredTokenStillExposesUserIdForLogout() {
    JwtProvider provider = new JwtProvider(properties(-1_000L));

    String token = provider.generateToken(7L, "alice", List.of("ROLE_USER"));

    assertThat(new JwtValidator(SECRET).validateToken(token)).isFalse();
    assertThat(provider.extractUserIdAllowingExpired(token)).contains(7L);
  }

  @Test
  @DisplayName("garbage and null tokens never yield a user id")
  void malformedTokenYieldsNoUserId() {
    JwtProvider provider = new JwtProvider(properties(60_000L));

    assertThat(provider.extractUserIdAllowingExpired("not-a-jwt")).isEmpty();
    assertThat(provider.extractUserIdAllowingExpired(null)).isEmpty();
  }
}
