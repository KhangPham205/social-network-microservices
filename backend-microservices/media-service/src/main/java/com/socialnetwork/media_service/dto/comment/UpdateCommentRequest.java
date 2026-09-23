package com.socialnetwork.media_service.dto.comment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateCommentRequest {

  @NotNull private Long commentId;

  private String content;

  /** Replacement attachment; uploading one also removes the current attachment. */
  private MultipartFile mediaFile;

  /** Removes the current attachment without uploading a new one. */
  private Boolean removeMedia;
}
