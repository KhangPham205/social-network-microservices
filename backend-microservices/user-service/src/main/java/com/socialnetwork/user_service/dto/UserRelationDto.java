package com.socialnetwork.user_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** A profile plus how the current viewer is related to it. */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRelationDto extends UserProfileDto {
  /** The viewer follows this user. */
  private boolean isFollowing;

  /** This user follows the viewer back. */
  private boolean isFollowedBy;

  private FriendshipResponse friendship;

  /** Number of friends the viewer and this user have in common. */
  private int mutualFriendsCount;
}
