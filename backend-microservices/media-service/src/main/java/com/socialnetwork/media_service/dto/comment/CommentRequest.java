package com.socialnetwork.media_service.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CommentRequest {

  @NotNull private Long postId;

  /** Id of the comment being replied to; null for a root comment. */
  private Long parentId;

  @NotBlank private String content;

  private MultipartFile mediaFile;
}
