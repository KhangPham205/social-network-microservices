package com.socialnetwork.media_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.media_service.dto.comment.CommentRequest;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.comment.UpdateCommentRequest;
import com.socialnetwork.media_service.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.MEDIA + "/comments")
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
}
