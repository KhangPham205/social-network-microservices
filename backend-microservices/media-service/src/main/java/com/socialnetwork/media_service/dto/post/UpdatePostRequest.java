package com.socialnetwork.media_service.dto.post;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequest {

  @NotNull private Long postId;

  private String content;

  private String accessModifier;

  /** URLs of the current attachments to keep; anything else is dropped from the post. */
  private List<String> keepMediaUrls;

  /** URLs to delete from storage; entries that do not belong to this post are ignored. */
  private List<String> removeMediaUrls;

  private List<MultipartFile> mediaFiles;
}
