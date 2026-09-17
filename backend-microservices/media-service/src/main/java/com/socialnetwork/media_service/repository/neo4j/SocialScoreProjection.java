package com.socialnetwork.media_service.repository.neo4j;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialScoreProjection {
  private Long postId;
  private Long socialScore;
}
