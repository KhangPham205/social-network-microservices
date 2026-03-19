package constants;

public final class WebSocketConstants {

  /**
   * Tiền tố cho các tin nhắn gửi TỪ client TỚI server. Phải khớp với
   * 'setApplicationDestinationPrefixes' trong WebSocketConfig.
   */
  public static final String APP_PREFIX = "/app";

  /**
   * Tiền tố cho các kênh (queue) 1-1 cho user. Phải khớp với 'setUserDestinationPrefix' trong
   * WebSocketConfig.
   */
  public static final String USER_PREFIX = "/user";

  /** Tiền tố cho các kênh (queue) chung. */
  public static final String QUEUE_PREFIX = "/queue";

  // --- Các Endpoint xử lý tin nhắn (phía Server) ---

  /** Endpoint để client gửi tin nhắn chat. Được xử lý bởi: ChatController.handleChatMessage */
  public static final String CHAT_SEND = APP_PREFIX + "/chat.send";

  /** Endpoint để client thông báo tham gia chat. Được xử lý bởi: ChatController.addUser */
  public static final String CHAT_ADD_USER = APP_PREFIX + "/chat.addUser";

  // --- Các Kênh (Destination) mà Client lắng nghe (subscribe) ---

  /**
   * Kênh thông báo (notification) riêng của mỗi user. Client sẽ subscribe:
   * /user/queue/notifications Server (NotificationService) sẽ gửi tới: /queue/notifications
   */
  public static final String NOTIFICATIONS_QUEUE = QUEUE_PREFIX + "/notifications";

  /**
   * Kênh chat chung của một cuộc hội thoại. Client sẽ subscribe: /queue/conversation/{id} Server
   * (MessageService) sẽ gửi tới: /queue/conversation/{id}
   */
  public static final String CHAT_CONVERSATION_QUEUE = QUEUE_PREFIX + "/conversation";
}
