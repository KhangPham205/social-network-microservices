package com.socialnetwork.user_service.repository.neo4j;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedFriendProjection {
  private Long id;
  private String displayName;
  private int mutualFriendsCount;
}
