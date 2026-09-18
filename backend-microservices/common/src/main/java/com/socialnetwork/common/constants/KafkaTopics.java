package com.socialnetwork.common.constants;

/**
 * Single source of truth for Kafka topic names. Producers and consumers must reference these
 * constants instead of string literals.
 */
public final class KafkaTopics {

  private KafkaTopics() {}

  /** auth-service -> user/media/notification: a credential was created (registration saga). */
  public static final String USER_CREATED = "user-created-topic";

  /** user-service -> auth-service: profile created, registration saga succeeded. */
  public static final String PROFILE_CREATED = "profile-created-topic";

  /** user-service -> auth-service: profile creation failed, compensate. */
  public static final String PROFILE_FAILED = "profile-failed-topic";

  /** user-service -> media/notification/chat: display name or avatar changed. */
  public static final String PROFILE_UPDATED = "profile-updated-topic";

  /** user-service -> chat-service/user-service(neo4j): friendship accepted or deleted. */
  public static final String FRIENDSHIP_EVENTS = "friendship-events";

  /** any service -> notification-service. */
  public static final String NOTIFICATION = "notification-topic";

  /** chat-service -> notification-service: new message for offline recipients. */
  public static final String CHAT_NOTIFICATION = "chat-notification-topic";

  /** media-service -> moderation-service / media-service(recommendation). */
  public static final String CONTENT_CREATED = "content-created-topic";

  /** chat-service -> moderation-service: message text to moderate. */
  public static final String MESSAGE_CREATED = "message-created-topic";

  /** moderation-service -> media-service / chat-service: block or unblock content. */
  public static final String MODERATION_ACTIONS = "moderation-actions";

  /** moderation-service -> auth-service: change an account status. */
  public static final String USER_MODERATION_ACTIONS = "user-moderation-actions";
}
