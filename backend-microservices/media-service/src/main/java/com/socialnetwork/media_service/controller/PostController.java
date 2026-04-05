package com.socialnetwork.media_service.controller;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vo.PageVO;

@RestController
@RequestMapping("/api/v1/media/posts")
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  @GetMapping("/feed")
  public ResponseEntity<PageVO<PostResponse>> getFeed(
      @ParameterObject Pageable pageable, @RequestParam(required = false) String filter) {
    return ResponseEntity.ok(postService.getFeed(pageable, filter));
  }

  //    @GetMapping("/explore")
  //    public ResponseEntity<PageVO<PostResponse>> getExploreFeed(
  //            @RequestParam(defaultValue = "0") int page,
  //            @RequestParam(defaultValue = "10") int size
  //    ) {
  //        return ResponseEntity.ok(postRecommendationService.getExploreFeed(page, size));
  //    }

  @GetMapping("/{postId}")
  public ResponseEntity<PostResponse> getPostById(@PathVariable Long postId) {
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
      @RequestParam Long originalPostId,
      @RequestParam(required = false) String caption,
      @RequestParam AccessScope accessScope) {
    return ResponseEntity.ok(postService.sharePost(originalPostId, caption, accessScope));
  }

  @GetMapping("/me")
  public ResponseEntity<PageVO<PostResponse>> getMyPosts(@ParameterObject Pageable pageable) {
    return ResponseEntity.ok(postService.getMyPosts(pageable));
  }

  @GetMapping("/user/{userId}")
  public ResponseEntity<PageVO<PostResponse>> getUserPosts(
      @PathVariable Long userId, @ParameterObject Pageable pageable) {
    return ResponseEntity.ok(postService.getUserPosts(userId, pageable));
  }

  @DeleteMapping("/{postId}")
  public ResponseEntity<Void> delete(@PathVariable Long postId) {
    postService.deletePost(postId);
    return ResponseEntity.noContent().build();
  }
}
