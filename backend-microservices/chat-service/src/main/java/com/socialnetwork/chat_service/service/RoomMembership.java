package com.socialnetwork.chat_service.service;

/** Membership lookup with no WebSocket or messaging dependencies, safe to use from interceptors. */
public interface RoomMembership {

  boolean isMember(Long roomId, Long userId);
}
