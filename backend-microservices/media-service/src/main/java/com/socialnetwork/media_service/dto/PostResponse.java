package com.socialnetwork.media_service.dto;

import com.socialnetwork.media_service.enums.AccessScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
  private Long id;
  private String content;
  private List<Map<String, String>> media;
  private AccessScope accessModifier;

  private int reactCount;
  private int commentCount;
  private int shareCount;

  private String authorName;
  private String authorAvatar;
  private Long authorId;

  // Nếu bài gốc không khả dụng => null
  private Long sharedPostId;
  private PostResponse sharedPost;

  private Object reactSummary;

  private long reportCount;
  private long complaintCount;

  private boolean isSystemBan;

  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;
}
