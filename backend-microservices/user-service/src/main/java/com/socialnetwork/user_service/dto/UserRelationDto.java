package com.socialnetwork.user_service.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRelationDto extends UserProfileDto {
  private boolean isFollowing; // mình đang follow họ
  private boolean isFollowedBy; // họ follow lại mình
  private FriendshipResponse friendship;
}
