package com.socialnetwork.user_service.dto;

import com.socialnetwork.user_service.model.Friendship;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.socialnetwork.common.vo.FriendshipStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendshipResponse {
  private String message;
  private FriendshipStatus status;
  private Long senderId;
  private Long receiverId;

  public static FriendshipResponse from(Friendship f, Long viewerId) {
    return FriendshipResponse.builder()
        .status(f.getStatus())
        .senderId(f.getSender().getId().equals(viewerId) ? viewerId : f.getReceiver().getId())
        .receiverId(f.getSender().getId().equals(viewerId) ? f.getReceiver().getId() : viewerId)
        .build();
  }
}
