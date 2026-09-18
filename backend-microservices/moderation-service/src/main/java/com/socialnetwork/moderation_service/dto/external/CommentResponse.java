package com.socialnetwork.moderation_service.dto.external;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
  private Long id;

  private Long authorId;
  private String authorName;
  private String authorAvatar;

  private ReactSummaryDto reactSummary;

  private Long postId;
  private Long parentId;

  private String content;
  private List<Map<String, String>> media;

  private Integer reactCount;
  private Integer childrenCount;

  private long reportCount;
  private long complaintCount;

  private Instant createdAt;
  private Instant updatedAt;

  private Integer depth;

  private Instant deletedAt;
}
