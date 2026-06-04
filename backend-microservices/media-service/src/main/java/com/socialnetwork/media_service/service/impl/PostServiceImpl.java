package com.socialnetwork.media_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.client.UserServiceClient;
import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.dto.post.UpdatePostRequest;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.enums.AccessScope;
import events.ContentCreatedEvent;
import com.socialnetwork.media_service.mapper.PostMapper;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.PostService;
import com.socialnetwork.media_service.service.ReactService;
import com.socialnetwork.media_service.service.StorageService;
import exception.AccessDeniedException;
import exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vo.PageVO;
import vo.TargetType;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

  private final PostRepository postRepository;
  private final UserCacheRepository userCacheRepository;
  private final UserServiceClient userServiceClient; // Gọi sang user-service
  private final StorageService storageService; // Xử lý upload Minio
  private final ReactService reactService;
  private final PostMapper postMapper;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  @CacheEvict(value = "newsfeed", allEntries = true)
  public PostResponse create(
      String content, String accessModifier, List<MultipartFile> mediaFiles) {
    Long currentUserId = getCurrentUserId();

    // 1. Lấy thông tin User từ Cache nội bộ (đã được đồng bộ qua Kafka trước đó)
    UserCache author =
        userCacheRepository
            .findById(currentUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("User cache not found. Please sync user data."));

    // 2. Upload file lên Minio
    List<Map<String, String>> mediaList = processMediaUploads(mediaFiles);

    // 3. Tạo Entity
    Post post =
        Post.builder()
            .author(author)
            .content(content)
            .media(mediaList)
            .accessModifier(
                accessModifier != null ? AccessScope.valueOf(accessModifier) : AccessScope.PUBLIC)
            .createdAt(Instant.now())
            .isSystemBan(false)
            .commentCount(0)
            .shareCount(0)
            .build();

    Post savedPost = postRepository.save(post);

    // 4. Bắn sự kiện ra Kafka cho AI Moderation check hoặc Notification Service
    try {
        ContentCreatedEvent event = new ContentCreatedEvent(
            savedPost.getId(),
            "POST",
            savedPost.getContent(),
            author.getId(),
            savedPost.getMedia());
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("content-created-topic", payload);
    } catch (Exception e) {
        log.error("Failed to send content-created event", e);
    }

    return toDtoWithDetails(savedPost, currentUserId);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"newsfeed", "post_details"},
      allEntries = true)
  public PostResponse update(UpdatePostRequest request) {
    Long currentUserId = getCurrentUserId();
    Post post =
        postRepository
            .findById(request.getPostId())
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    // Kiểm tra quyền (chỉ tác giả hoặc Admin mới được sửa)
    if (!post.getAuthor().getId().equals(currentUserId) && !hasRole("ADMIN")) {
      throw new AccessDeniedException("You are not authorized to update this post.");
    }

    // Cập nhật nội dung & quyền riêng tư
    if (request.getContent() != null) post.setContent(request.getContent());
    if (request.getAccessModifier() != null)
      post.setAccessModifier(AccessScope.valueOf(request.getAccessModifier()));

    List<Map<String, String>> currentMedia = new ArrayList<>();

    // Giữ lại media cũ
    if (request.getKeepMediaUrls() != null) {
      currentMedia.addAll(
          post.getMedia().stream()
              .filter(m -> request.getKeepMediaUrls().contains(m.get("url")))
              .toList());
    }

    // Xóa media không dùng nữa khỏi Minio
    if (request.getRemoveMediaUrls() != null) {
      request.getRemoveMediaUrls().forEach(storageService::deleteFile);
    }

    // Upload media mới
    if (request.getMediaFiles() != null && !request.getMediaFiles().isEmpty()) {
      currentMedia.addAll(processMediaUploads(request.getMediaFiles()));
    }

    post.setMedia(currentMedia);
    post.setUpdatedAt(Instant.now());

    Post updatedPost = postRepository.save(post);
    return toDtoWithDetails(updatedPost, currentUserId);
  }

  @Override
  @Transactional
  public void updateSystemBanStatus(Long postId, boolean isBanned) {
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    post.setDeletedAt(isBanned ? Instant.now() : null);
    post.setIsSystemBan(isBanned);
    postRepository.save(post);
    log.info("Đã cập nhật SystemBan = {} cho Post ID: {}", isBanned, postId);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "post_details", key = "#postId")
  public PostResponse getPostById(Long postId) {
    Long currentUserId = getCurrentUserId();
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

    checkViewPermission(post, currentUserId);
    return toDtoWithDetails(post, currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "newsfeed", key = "#currentUserId + '_' + #pageable.pageNumber")
  public PageVO<PostResponse> getFeed(Pageable pageable, String filter) {
    Long currentUserId = getCurrentUserId();

    List<Long> networkIds = new ArrayList<>(userServiceClient.getNetworkIds(currentUserId));
    if (!networkIds.contains(currentUserId)) {
      networkIds.add(currentUserId);
    }

    Specification<Post> spec =
        (root, query, cb) -> {
          Predicate authorInNetwork = root.get("author").get("id").in(networkIds);
          Predicate notBanned = cb.isFalse(root.get("isSystemBan"));
          Predicate notDeleted = cb.isNull(root.get("deletedAt"));
          Predicate isMyPost = cb.equal(root.get("author").get("id"), currentUserId);
          Predicate visibilityCondition = cb.or(notBanned, notDeleted, isMyPost);

          // Logic quyền xem:
          Predicate isPublic = cb.equal(root.get("accessModifier"), AccessScope.PUBLIC);
          Predicate isFriendScope =
              cb.and(
                  cb.equal(root.get("accessModifier"), AccessScope.FRIENDS),
                  root.get("author")
                      .get("id")
                      .in(networkIds) // Bài bạn bè & tác giả nằm trong network
                  );
          Predicate isPrivateAndMine =
              cb.and(
                  cb.equal(root.get("accessModifier"), AccessScope.PRIVATE),
                  cb.equal(root.get("author").get("id"), currentUserId));

          Predicate accessControl = cb.or(isPublic, isFriendScope, isPrivateAndMine);
          Predicate finalPredicate = cb.and(authorInNetwork, visibilityCondition, accessControl);

          // Filter theo keyword nếu có
          if (filter != null && !filter.isBlank()) {
            Predicate filterPredicate =
                cb.like(cb.lower(root.get("content")), "%" + filter.toLowerCase() + "%");
            finalPredicate = cb.and(finalPredicate, filterPredicate);
          }

          return finalPredicate;
        };

    Page<Post> postPage = postRepository.findAll(spec, pageable);
    return buildPageVO(postPage, currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<PostResponse> getUserPosts(Long targetUserId, Pageable pageable) {
    Long currentUserId = getCurrentUserId();

    // Gọi user-service để check xem có phải bạn bè không
    boolean isFriend = userServiceClient.isFriend(currentUserId, targetUserId);
    boolean isSelf = currentUserId.equals(targetUserId);

    Specification<Post> spec =
        (root, query, cb) -> {
          Predicate authorMatch = cb.equal(root.get("author").get("id"), targetUserId);
          Predicate notDeleted = cb.isNull(root.get("deletedAt"));
          Predicate notBanned = cb.isFalse(root.get("isSystemBan"));

          if (isSelf) return cb.and(authorMatch);

          Predicate publicPosts = cb.equal(root.get("accessModifier"), AccessScope.PUBLIC);
          if (isFriend) {
            Predicate friendPosts = cb.equal(root.get("accessModifier"), AccessScope.FRIENDS);
            return cb.and(authorMatch, notDeleted, notBanned, cb.or(publicPosts, friendPosts));
          }

          return cb.and(authorMatch, notDeleted, notBanned, publicPosts); // Người lạ chỉ xem public
        };

    Page<Post> postPage = postRepository.findAll(spec, pageable);
    return buildPageVO(postPage, currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<PostResponse> getMyPosts(Pageable pageable) {
    return getUserPosts(getCurrentUserId(), pageable);
  }

  @Override
  @Transactional
  public PostResponse sharePost(Long originalPostId, String caption, AccessScope accessScope) {
    Long currentUserId = getCurrentUserId();
    UserCache currentUser =
        userCacheRepository
            .findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User cache not found."));

    Post original =
        postRepository
            .findById(originalPostId)
            .orElseThrow(() -> new ResourceNotFoundException("Original post not found"));

    checkViewPermission(original, currentUserId);

    if (original.getAccessModifier() == AccessScope.PRIVATE) {
      throw new IllegalArgumentException("Cannot share a private post.");
    }

    Post shared =
        Post.builder()
            .author(currentUser)
            .content(caption)
            .sharedPost(original)
            .accessModifier(accessScope)
            .createdAt(Instant.now())
            .build();

    // Tăng biến đếm share của bài gốc (có thể dùng Redis thay thế nếu muốn tối ưu)
    original.setShareCount(original.getShareCount() + 1);
    postRepository.save(original);

    Post savedSharedPost = postRepository.save(shared);
    return toDtoWithDetails(savedSharedPost, currentUserId);
  }

  @Override
  @Transactional
  @CacheEvict(
      value = {"newsfeed", "post_details"},
      allEntries = true)
  public void deletePost(Long postId) {
    Long currentUserId = getCurrentUserId();
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (!post.getAuthor().getId().equals(currentUserId) && !hasRole("ADMIN")) {
      throw new AccessDeniedException("You are not authorized to delete this post.");
    }

    postRepository.deleteById(post.getId());

    // Optional: Bắn event ra Kafka để các service khác biết bài này đã bị xóa
  }

  @Override
  public Long getPostOwnerId(Long postId) {
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    return post.getAuthor().getId();
  }

  @Override
  public List<PostResponse> getPostsByIds(List<Long> ids) {
    List<Post> posts = postRepository.findAllById(ids);
    return posts.stream().map(postMapper::toDto).toList();
  }

  // ==========================================
  // PRIVATE HELPER METHODS
  // ==========================================

  private Long getCurrentUserId() {
    // Trích xuất ID từ JWT Token thông qua Spring Security Context
    // Cần đảm bảo JwtAuthenticationFilter đã parse token và set Principal là User ID
    return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  private boolean hasRole(String role) {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role) || a.getAuthority().equals(role));
  }

  private void checkViewPermission(Post post, Long viewerId) {
    Long authorId = post.getAuthor().getId();
    if (authorId.equals(viewerId) || hasRole("ADMIN")) return;

    if (post.getAccessModifier() == AccessScope.PRIVATE) {
      throw new AccessDeniedException("This post is private.");
    }

    if (post.getDeletedAt() != null && !authorId.equals(viewerId)) {
      throw new ResourceNotFoundException("Post not found or has been deleted.");
    }

    if (post.getAccessModifier() == AccessScope.FRIENDS) {
      if (!userServiceClient.isFriend(authorId, viewerId)) {
        throw new AccessDeniedException("Only friends can view this post.");
      }
    }
  }

  private List<Map<String, String>> processMediaUploads(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) return new ArrayList<>();
    return files.stream()
        .map(
            file -> {
              String url = storageService.saveFile(file, "posts"); // Minio Logic
              String ext = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
              String type = isVideo(ext) ? "video" : "image";
              return Map.of("type", type, "url", url);
            })
        .collect(Collectors.toList());
  }

  private String getExtension(String filename) {
    int dot = filename.lastIndexOf(".");
    return dot != -1 ? filename.substring(dot + 1).toLowerCase() : "";
  }

  private boolean isVideo(String ext) {
    return List.of("mp4", "webm", "ogg", "mov", "quicktime").contains(ext.toLowerCase());
  }

  private PageVO<PostResponse> buildPageVO(Page<Post> postPage, Long currentUserId) {
    List<Post> posts = postPage.getContent();
    if (posts.isEmpty()) {
      return PageVO.<PostResponse>builder()
          .page(postPage.getNumber())
          .size(postPage.getSize())
          .totalElements(postPage.getTotalElements())
          .totalPages(postPage.getTotalPages())
          .numberOfElements(0)
          .content(List.of())
          .build();
    }

    // 1. Lấy ID của tất cả bài viết trong trang hiện tại
    List<Long> postIds = posts.stream().map(Post::getId).toList();

    // 2. Lấy luôn ID của các bài được share (nếu có) để query React 1 thể
    List<Long> sharedPostIds =
        posts.stream().map(Post::getSharedPost).filter(Objects::nonNull).map(Post::getId).toList();

    // 3. Gọi ReactService ĐÚNG 1 LẦN để lấy toàn bộ Summary (cực nhanh)
    Map<Long, ReactSummaryDto> reactMap =
        reactService.getReactSummaries(postIds, currentUserId, TargetType.POST);

    Map<Long, ReactSummaryDto> sharedReactMap =
        sharedPostIds.isEmpty()
            ? Map.of()
            : reactService.getReactSummaries(sharedPostIds, currentUserId, TargetType.POST);

    // 4. Map vào DTO
    List<PostResponse> content =
        posts.stream()
            .map(
                post -> {
                  PostResponse dto = postMapper.toDto(post);

                  // Gắn React cho bài gốc
                  dto.setReactSummary(
                      reactMap.getOrDefault(post.getId(), new ReactSummaryDto(Map.of(), 0L, null)));

                  // Gắn React cho bài Share
                  if (post.getSharedPost() != null) {
                    try {
                      checkViewPermission(post.getSharedPost(), currentUserId);
                      PostResponse sharedDto = postMapper.toDto(post.getSharedPost());
                      sharedDto.setReactSummary(
                          sharedReactMap.getOrDefault(
                              post.getSharedPost().getId(),
                              new ReactSummaryDto(Map.of(), 0L, null)));
                      dto.setSharedPost(sharedDto);
                    } catch (AccessDeniedException e) {
                      dto.setSharedPost(null); // Không có quyền xem bài gốc
                    }
                  }
                  return dto;
                })
            .toList();

    return PageVO.<PostResponse>builder()
        .page(postPage.getNumber())
        .size(postPage.getSize())
        .totalElements(postPage.getTotalElements())
        .totalPages(postPage.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  private PostResponse toDtoWithDetails(Post post, Long currentUserId) {
    PostResponse dto = postMapper.toDto(post);

    // Lấy React cho 1 bài duy nhất
    dto.setReactSummary(reactService.getReactSummary(post.getId(), TargetType.POST, currentUserId));

    if (post.getSharedPost() != null) {
      try {
        checkViewPermission(post.getSharedPost(), currentUserId);
        PostResponse sharedDto = postMapper.toDto(post.getSharedPost());
        sharedDto.setReactSummary(
            reactService.getReactSummary(
                post.getSharedPost().getId(), TargetType.POST, currentUserId));
        dto.setSharedPost(sharedDto);
      } catch (AccessDeniedException e) {
        dto.setSharedPost(null);
      }
    }
    return dto;
  }
}
