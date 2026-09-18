package com.socialnetwork.common.security;

import static com.socialnetwork.common.constants.SecurityConstants.ROLE_PREFIX;

import com.socialnetwork.common.exception.AccessDeniedException;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static helpers over the current {@link Authentication} set by {@link JwtAuthenticationFilter}. */
public final class SecurityUtils {

  private SecurityUtils() {}

  public static Optional<Authentication> currentAuthentication() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    return Optional.of(auth);
  }

  /** Current user id, empty when anonymous. */
  public static Optional<Long> findCurrentUserId() {
    return currentAuthentication()
        .map(Authentication::getName)
        .flatMap(
            name -> {
              try {
                return Optional.of(Long.parseLong(name));
              } catch (NumberFormatException e) {
                return Optional.empty();
              }
            });
  }

  /** Current user id; throws 403 when the request is anonymous. */
  public static Long getCurrentUserId() {
    return findCurrentUserId()
        .orElseThrow(() -> new AccessDeniedException("User is not authenticated"));
  }

  /** Raw access token of the current request, for propagation to downstream services. */
  public static Optional<String> getCurrentToken() {
    return currentAuthentication()
        .map(Authentication::getDetails)
        .filter(JwtAuthenticationDetails.class::isInstance)
        .map(d -> ((JwtAuthenticationDetails) d).getToken());
  }

  public static boolean hasAuthority(String authority) {
    return currentAuthentication()
        .map(
            auth ->
                auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(authority::equals))
        .orElse(false);
  }

  /** {@code hasRole("ADMIN")} matches authority {@code ROLE_ADMIN}. */
  public static boolean hasRole(String role) {
    String name = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
    return hasAuthority(name);
  }

  public static boolean isAuthenticated() {
    return currentAuthentication().isPresent();
  }
}
