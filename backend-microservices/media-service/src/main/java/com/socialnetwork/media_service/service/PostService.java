package com.socialnetwork.media_service.service;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.dto.post.UpdatePostRequest;
import com.socialnetwork.media_service.enums.AccessScope;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface PostService {

  PostResponse create(String content, String accessModifier, List<MultipartFile> mediaFiles);

  PostResponse update(UpdatePostRequest request);

  /** Applied by the moderation listener; keeps the post row but hides it from everybody else. */
  void updateSystemBanStatus(Long postId, boolean isBanned);

  PostResponse getPostById(Long postId);

  PageVO<PostResponse> getMyPosts(Pageable pageable);

  PageVO<PostResponse> getUserPosts(Long userId, Pageable pageable);

  PostResponse sharePost(Long originalPostId, String caption, AccessScope accessScope);

  /** Recommended feed, falling back to a chronological network feed when ranking is unavailable. */
  PageVO<PostResponse> getFeed(Pageable pageable, String filter);

  /** Soft delete: the row stays so shares and comments keep their foreign keys. */
  void deletePost(Long postId);

  /** Internal API: owner of a post, including removed ones, for moderation. */
  Long getPostOwnerId(Long postId);

  /** Internal API: batch lookup used by moderation-service. */
  List<PostResponse> getPostsByIds(List<Long> ids);
}
