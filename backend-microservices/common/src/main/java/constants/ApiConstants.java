package constants;

/**
 * Lớp (class) final chứa các hằng số (constants) cho các đường dẫn API. private constructor để ngăn
 * chặn việc khởi tạo (instantiation).
 */
public final class ApiConstants {

  /** Constructor riêng tư để ngăn không cho ai khởi tạo lớp tiện ích này. */
  private ApiConstants() {
    // Ngăn chặn việc tạo đối tượng
  }

  // ================================================================
  // == Base API Paths
  // ================================================================

  public static final String API_V1 = "/api/v1";

  // ================================================================
  // == API Controllers (Dùng cho @RequestMapping ở cấp Class)
  // ================================================================

  /** Đường dẫn gốc cho Authentication Controller (Ví dụ: /api/v1/auth) */
  public static final String AUTH = API_V1 + "/auth";

  /** Đường dẫn gốc cho User Controller (Ví dụ: /api/v1/users) */
  public static final String USERS = API_V1 + "/users";

  /** Đường dẫn gốc cho Post Controller (Ví dụ: /api/v1/posts) */
  public static final String POSTS = API_V1 + "/posts";

  /** Đường dẫn gốc cho Comment Controller (Ví dụ: /api/v1/comments) */
  public static final String COMMENTS = API_V1 + "/comments";

  /** Đường dẫn gốc cho React Controller (Ví dụ: /api/v1/reacts) */
  public static final String REACTS = API_V1 + "/reacts";

  /** Đường dẫn gốc cho Friendship Controller (Ví dụ: /api/v1/friendship) */
  public static final String FRIENDSHIP = API_V1 + "/friends";

  /** Đường dẫn gốc cho Message/Conversation Controller (Ví dụ: /api/v1/messages) */
  public static final String MESSAGES = API_V1 + "/messages";

  /** Đường dẫn gốc cho Report Controller (Ví dụ: /api/v1/reports) */
  public static final String REPORTS = API_V1 + "/reports";

  /** Đường dẫn gốc cho Complaint Controller (Ví dụ: /api/v1/complaints) */
  public static final String COMPLAINTS = API_V1 + "/complaints";

  /** Đường dẫn gốc cho Moderation Controller (Ví dụ: /api/v1/moderation) */
  public static final String MODERATION = API_V1 + "/moderation";

  /** Đường dẫn gốc cho Recommendations Controller (Ví dụ: /api/v1/recommendations) */
  public static final String RECOMMENDATIONS = API_V1 + "/recommendations";

  // ================================================================
  // == Public/Non-API Paths
  // ================================================================

  /** Đường dẫn công khai để truy cập file (ảnh, video) */
  public static final String FILES = "/files";

  /** Đường dẫn công khai cho WebSocket */
  public static final String WEBSOCKET = "/ws";

  /** Đường dẫn gốc cho Notification Controller (Ví dụ: /api/v1/notifications) */
  public static final String NOTIFICATIONS = API_V1 + "/notifications";

  // ================================================================
  // == Whitelists cho Spring Security (Mảng các đường dẫn)
  // ================================================================

  /** Các đường dẫn Swagger (cho phép truy cập documentation). */
  public static final String[] SWAGGER_WHITELIST = {
    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
  };

  /** Các đường dẫn API công khai (không cần xác thực). */
  public static final String[] PUBLIC_API_WHITELIST = {
    AUTH + "/**", // Toàn bộ auth controller (login, register, refresh)
    WEBSOCKET + "/**", // Cho phép kết nối WebSocket
    FILES + "/**", // Cho phép truy cập file (ảnh, video)
  };
}
