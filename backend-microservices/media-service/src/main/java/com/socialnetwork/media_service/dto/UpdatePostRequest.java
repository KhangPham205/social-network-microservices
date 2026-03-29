package com.socialnetwork.media_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequest {
  private Long postId;
  private String content;
  private String accessModifier;
  private List<String> keepMediaUrls;
  private List<String> removeMediaUrls;

  @Nullable private List<MultipartFile> mediaFiles;
}
