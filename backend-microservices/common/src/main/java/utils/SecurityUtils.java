package utils;

import exception.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

  /** Lấy ID của user đang login từ Header do API Gateway truyền xuống */
  public static Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AccessDeniedException("User is not authenticated");
    }

    String name = authentication.getName();

    // Kiểm tra nếu là anonymous user (do AnonymousAuthenticationFilter set)
    if (name == null || name.equals("anonymousUser")) {
      throw new AccessDeniedException(
          "User is not authenticated. Please provide a valid JWT token.");
    }

    try {
      return Long.parseLong(name);
    } catch (NumberFormatException e) {
      throw new AccessDeniedException(
          "Invalid user ID format in token. Expected numeric ID but got: " + name);
    }
  }
}
