package com.socialnetwork.common.constants;

/**
 * STOMP destinations. Values under "Inbound" are relative to the application prefix (Spring strips
 * {@link #APP_PREFIX} before matching {@code @MessageMapping}).
 */
public final class WebSocketConstants {

  private WebSocketConstants() {}

  public static final String APP_PREFIX = "/app";
  public static final String USER_PREFIX = "/user";
  public static final String QUEUE_PREFIX = "/queue";
  public static final String TOPIC_PREFIX = "/topic";

  // ---- Inbound (client -> server), relative to APP_PREFIX ----
  public static final String CHAT_SEND = "/chat.send";
  public static final String CHAT_READ = "/chat.read";
  public static final String CHAT_ADD_USER = "/chat.addUser";

  // ---- Outbound (server -> client) ----
  /** Per-user notification queue: client subscribes to /user/queue/notifications. */
  public static final String NOTIFICATIONS_QUEUE = QUEUE_PREFIX + "/notifications";

  /** Per-user unread counter: client subscribes to /user/queue/notification-summary. */
  public static final String NOTIFICATION_SUMMARY_QUEUE = QUEUE_PREFIX + "/notification-summary";

  /** Room-wide chat messages: /queue/conversation/{roomId}. */
  public static final String CONVERSATION_QUEUE = QUEUE_PREFIX + "/conversation";

  /** Room-wide system events (read receipts, deletions): /topic/conversation/{roomId}. */
  public static final String CONVERSATION_TOPIC = TOPIC_PREFIX + "/conversation";

  /** Presence broadcast. */
  public static final String PUBLIC_TOPIC = TOPIC_PREFIX + "/public";
}
