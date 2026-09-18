package com.socialnetwork.notification_service.dto;

import java.time.Instant;

/** Broadcast on /topic/public when a user's STOMP session connects or disconnects. */
public record PresenceEvent(Type type, Long userId, Instant timestamp) {

  public enum Type {
    USER_ONLINE,
    USER_OFFLINE
  }

  public static PresenceEvent online(Long userId) {
    return new PresenceEvent(Type.USER_ONLINE, userId, Instant.now());
  }

  public static PresenceEvent offline(Long userId) {
    return new PresenceEvent(Type.USER_OFFLINE, userId, Instant.now());
  }
}
