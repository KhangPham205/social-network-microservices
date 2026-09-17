package com.socialnetwork.user_service.events;

public record FriendshipDeletedEvent(Long user1Id, Long user2Id) {}
