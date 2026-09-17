package events;

public record NotificationEvent(
    Long actorId,
    Long receiverId,
    String type, // "REACT_POST", "COMMENT_POST"...
    Long targetId,
    Long postId) {}
