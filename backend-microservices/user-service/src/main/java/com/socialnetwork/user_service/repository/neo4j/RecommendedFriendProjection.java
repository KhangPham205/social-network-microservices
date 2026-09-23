package com.socialnetwork.user_service.repository.neo4j;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Row of the friend-of-friend query: a candidate plus how many friends they share. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedFriendProjection {
  private Long id;
  private String displayName;
  private int mutualFriendsCount;
}
