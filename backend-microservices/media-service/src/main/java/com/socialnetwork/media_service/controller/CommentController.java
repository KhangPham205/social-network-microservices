package com.socialnetwork.media_service.controller;

import com.socialnetwork.media_service.dto.comment.CommentRequest;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.comment.UpdateCommentRequest;
import com.socialnetwork.media_service.service.CommentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vo.PageVO;

@RestController
@RequestMapping("/api/v1/media/comments")
@RequiredArgsConstructor
public class CommentController {

  private final CommentService commentService;

  @GetMapping("/post/{postId}")
  public ResponseEntity<PageVO<CommentResponse>> getCommentsByPost(
      @PathVariable("postId") Long postId, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(commentService.getCommentsByPost(postId, pageable));
  }

  @GetMapping("/{commentId}/replies")
  public ResponseEntity<PageVO<CommentResponse>> getReplies(
      @PathVariable("commentId") Long commentId, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(commentService.getReplies(commentId, pageable));
  }

  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommentResponse> createComment(@ModelAttribute CommentRequest request) {
    return ResponseEntity.ok(commentService.createComment(request));
  }

  @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<CommentResponse> updateComment(
      @ModelAttribute UpdateCommentRequest request) {
    return ResponseEntity.ok(commentService.updateComment(request));
  }

  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> deleteComment(@PathVariable("commentId") Long commentId) {
    commentService.deleteComment(commentId);
    return ResponseEntity.noContent().build();
  }

  // ================= API NỘI BỘ CHO MODERATION SERVICE =================

  @GetMapping("/{commentId}/owner-id")
  public ResponseEntity<Long> getCommentOwnerId(@PathVariable("commentId") Long commentId) {
    return ResponseEntity.ok(commentService.getCommentOwnerId(commentId));
  }

  @PostMapping("/batch")
  public ResponseEntity<List<CommentResponse>> getCommentsByIds(@RequestBody List<Long> ids) {
    return ResponseEntity.ok(commentService.getCommentsByIds(ids));
  }
}
