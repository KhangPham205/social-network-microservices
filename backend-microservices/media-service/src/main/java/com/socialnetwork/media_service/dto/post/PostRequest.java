package com.socialnetwork.media_service.dto.post;

import com.socialnetwork.media_service.enums.AccessScope;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class PostRequest {
  private Long postId;
  private String content;
  private AccessScope accessModifier;
  private Long sharedPostId;
  private List<Map<String, Object>> media;
}
