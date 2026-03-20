package com.socialnetwork.user_service.events;

public record FriendshipAcceptedEvent(Long senderId, Long receiverId) {}
