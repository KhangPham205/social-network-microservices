package events;

public record FriendRequestEvent(Long senderId, Long receiverId, String type) {

  public static FriendRequestEvent friendRequest(Long senderId, Long receiverId) {
    return new FriendRequestEvent(senderId, receiverId, "FRIEND_REQUEST");
  }
}
