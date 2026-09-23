package com.socialnetwork.media_service.controller;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.dto.post.UpdatePostRequest;
import com.socialnetwork.media_service.enums.AccessScope;
import com.socialnetwork.media_service.service.PostService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiConstants.MEDIA + "/posts")
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  @GetMapping("/feed")
  public ResponseEntity<PageVO<PostResponse>> getFeed(
      @ParameterObject Pageable pageable,
      @RequestParam(value = "filter", required = false) String filter) {
    return ResponseEntity.ok(postService.getFeed(pageable, filter));
  }

  @GetMapping("/{postId}")
  public ResponseEntity<PostResponse> getPostById(@PathVariable("postId") Long postId) {
    return ResponseEntity.ok(postService.getPostById(postId));
  }

  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PostResponse> create(
      @RequestPart("content") String content,
      @RequestPart(value = "accessModifier", required = false) String accessModifier,
      @Parameter(schema = @Schema(type = "string", format = "binary"))
          @RequestPart(value = "media", required = false)
          List<MultipartFile> mediaFiles) {
    return ResponseEntity.ok(postService.create(content, accessModifier, mediaFiles));
  }

  @PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PostResponse> update(@Valid @ModelAttribute UpdatePostRequest request) {
    return ResponseEntity.ok(postService.update(request));
  }

  @PostMapping("/share")
  public ResponseEntity<PostResponse> sharePost(
      @RequestParam("originalPostId") Long originalPostId,
      @RequestParam(value = "caption", required = false) String caption,
      @RequestParam("accessScope") AccessScope accessScope) {
    return ResponseEntity.ok(postService.sharePost(originalPostId, caption, accessScope));
  }

  @GetMapping("/me")
  public ResponseEntity<PageVO<PostResponse>> getMyPosts(@ParameterObject Pageable pageable) {
    return ResponseEntity.ok(postService.getMyPosts(pageable));
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<PageVO<PostResponse>> getUserPosts(
      @PathVariable("userId") Long userId, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(postService.getUserPosts(userId, pageable));
  }

  @DeleteMapping("/{postId}")
  public ResponseEntity<Void> delete(@PathVariable("postId") Long postId) {
    postService.deletePost(postId);
    return ResponseEntity.noContent().build();
  }
}
