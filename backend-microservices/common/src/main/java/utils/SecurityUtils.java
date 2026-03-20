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

    if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new AccessDeniedException("User is not authenticated");
    }

    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException e) {
      throw new AccessDeniedException("Invalid user ID format in token");
    }
  }
}
