package com.socialnetwork.chat_service.dto;

import java.time.Instant;

/**
 * Lightweight notification pushed to {@code /topic/conversation/{roomId}} for things that are not
 * messages: read receipts, revocations and conversation lifecycle changes.
 */
public record RoomEvent(
    String type, Long conversationId, String messageId, Long actorId, Instant timestamp) {

  public static final String MESSAGE_REVOKED = "MESSAGE_REVOKED";
  public static final String MESSAGE_READ = "MESSAGE_READ";
  public static final String CONVERSATION_DELETED = "CONVERSATION_DELETED";
  public static final String CONVERSATION_ARCHIVED = "CONVERSATION_ARCHIVED";

  public static RoomEvent messageRevoked(Long conversationId, String messageId) {
    return new RoomEvent(MESSAGE_REVOKED, conversationId, messageId, null, Instant.now());
  }

  public static RoomEvent messageRead(Long conversationId, String messageId, Long readerId) {
    return new RoomEvent(MESSAGE_READ, conversationId, messageId, readerId, Instant.now());
  }

  public static RoomEvent conversationDeleted(Long conversationId) {
    return new RoomEvent(CONVERSATION_DELETED, conversationId, null, null, Instant.now());
  }

  public static RoomEvent conversationArchived(Long conversationId) {
    return new RoomEvent(CONVERSATION_ARCHIVED, conversationId, null, null, Instant.now());
  }
}
