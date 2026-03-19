package utils;

import exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class SecurityUtils {

  /**
   * Lấy ID của user đang login từ Header do API Gateway truyền xuống
   */
  public static Long getCurrentUserId() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      throw new AccessDeniedException("No request context found");
    }

    HttpServletRequest request = attributes.getRequest();
    String userIdStr = request.getHeader("X-User-Id"); // Tên header này do bạn cấu hình ở API Gateway

    if (userIdStr == null || userIdStr.isEmpty()) {
      throw new AccessDeniedException("User is not authenticated");
    }

    return Long.parseLong(userIdStr);
  }
}
