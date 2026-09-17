package com.socialnetwork.media_service.service;

import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.dto.post.UpdatePostRequest;
import com.socialnetwork.media_service.enums.AccessScope;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vo.PageVO;

public interface PostService {
  PostResponse create(String content, String accessModifier, List<MultipartFile> mediaFiles);

  PostResponse update(UpdatePostRequest request);

  void updateSystemBanStatus(Long postId, boolean isBanned);

  PostResponse getPostById(Long postId);

  PageVO<PostResponse> getMyPosts(Pageable pageable);

  PageVO<PostResponse> getUserPosts(Long userId, Pageable pageable);

  PostResponse sharePost(Long originalPostId, String caption, AccessScope accessScope);

  PageVO<PostResponse> getFeed(Pageable pageable, String filter);

  @Transactional
  void deletePost(Long postId);

  Long getPostOwnerId(Long postId);

  List<PostResponse> getPostsByIds(List<Long> ids);
}
