package com.socialnetwork.auth_service.util;

import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Tiện ích lấy user hiện tại (dùng được cả trong REST + Service). */
public final class SecurityUtils {

  private SecurityUtils() {}

  public static Optional<String> getCurrentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    return Optional.ofNullable(auth.getName());
  }

  public static Optional<UserCredential> getCurrentUserCredential(UserCredentialRepository repo) {
    return getCurrentUsername().flatMap(repo::findByUsername);
  }

  public static Long getCurrentUserId(UserCredentialRepository repo) {
    String username =
        getCurrentUsername().orElseThrow(() -> new RuntimeException("User not authenticated"));
    return repo.findByUsername(username)
        .map(UserCredential::getId)
        .orElseThrow(() -> new RuntimeException("User not found"));
  }
}
