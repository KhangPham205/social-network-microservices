package events;

public record FriendRequestEvent(Long senderId, Long receiverId, String type) {}
