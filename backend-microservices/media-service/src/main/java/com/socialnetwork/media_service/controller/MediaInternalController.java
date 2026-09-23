package com.socialnetwork.media_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API used by moderation-service. Reachable only with a valid
 * {@code X-Internal-Token}: {@code JwtSecurityConfigurer} restricts {@code /api/v1/*&#47;internal/**}
 * to {@code ROLE_INTERNAL} and the gateway answers 404 to anything containing {@code /internal/}.
 */
@RestController
@RequestMapping(ApiConstants.MEDIA + ApiConstants.INTERNAL)
@RequiredArgsConstructor
public class MediaInternalController {

  private final PostService postService;
  private final CommentService commentService;

  @GetMapping("/posts/{postId}/owner-id")
  public ResponseEntity<Long> getPostOwnerId(@PathVariable("postId") Long postId) {
    return ResponseEntity.ok(postService.getPostOwnerId(postId));
  }

  @PostMapping("/posts/batch")
  public ResponseEntity<List<PostResponse>> getPostsByIds(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(postService.getPostsByIds(ids));
  }

  @GetMapping("/comments/{commentId}/owner-id")
  public ResponseEntity<Long> getCommentOwnerId(@PathVariable("commentId") Long commentId) {
    return ResponseEntity.ok(commentService.getCommentOwnerId(commentId));
  }

  @PostMapping("/comments/batch")
  public ResponseEntity<List<CommentResponse>> getCommentsByIds(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(commentService.getCommentsByIds(ids));
  }
}
