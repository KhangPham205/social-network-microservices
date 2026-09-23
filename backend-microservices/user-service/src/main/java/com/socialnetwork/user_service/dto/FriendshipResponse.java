package com.socialnetwork.user_service.dto;

import com.socialnetwork.common.vo.FriendshipStatus;
import com.socialnetwork.user_service.model.Friendship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendshipResponse {
  private String message;
  private FriendshipStatus status;
  private Long senderId;
  private Long receiverId;

  /** Keeps the real direction of the row: sender is whoever created it, whoever is looking. */
  public static FriendshipResponse from(Friendship friendship) {
    return FriendshipResponse.builder()
        .status(friendship.getStatus())
        .senderId(friendship.getSender().getId())
        .receiverId(friendship.getReceiver().getId())
        .build();
  }

  /** Placeholder returned when the two users have no row at all. */
  public static FriendshipResponse none(Long viewerId, Long targetId) {
    return FriendshipResponse.builder()
        .status(FriendshipStatus.NONE)
        .senderId(viewerId)
        .receiverId(targetId)
        .build();
  }
}
