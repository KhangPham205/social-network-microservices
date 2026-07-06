package com.socialnetwork.user_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedFriendDto {
  private Long id;
  private String displayName;
  private int mutualFriendsCount;
}
