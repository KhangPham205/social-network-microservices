package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.media_service.client.UserServiceClient;
import com.socialnetwork.media_service.dto.PostResponse;
import com.socialnetwork.media_service.dto.UpdatePostRequest;
import com.socialnetwork.media_service.enums.AccessScope;
import com.socialnetwork.media_service.events.ContentCreatedEvent;
import com.socialnetwork.media_service.mapper.PostMapper;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.PostService;
import com.socialnetwork.media_service.service.StorageService;
import exception.AccessDeniedException;
import exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vo.PageVO;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

  private final PostRepository postRepository;
  private final UserCacheRepository userCacheRepository;
  private final UserServiceClient userServiceClient; // Gọi sang user-service
  private final StorageService storageService; // Xử lý upload Minio
  private final PostMapper postMapper;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  @Transactional
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
    kafkaTemplate.send(
        "content-created-topic",
        new ContentCreatedEvent(
            savedPost.getId(),
            "POST",
            savedPost.getContent(),
            author.getId(),
            savedPost.getMedia()));

    return toDtoWithDetails(savedPost, currentUserId);
  }

  @Override
  @Transactional
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
  @Transactional(readOnly = true)
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
  public PageVO<PostResponse> getFeed(Pageable pageable, String filter) {
    Long currentUserId = getCurrentUserId();

    // 1. Gọi HTTP sang user-service để lấy danh sách ID (Bạn bè + Đang Follow)
    List<Long> networkIds = new ArrayList<>(userServiceClient.getNetworkIds(currentUserId));
    if (!networkIds.contains(currentUserId)) {
      networkIds.add(currentUserId); // Luôn xem được bài của chính mình
    }

    // 2. Build Query động (Specification)
    Specification<Post> spec =
        (root, query, cb) -> {
          Predicate authorInNetwork = root.get("author").get("id").in(networkIds);
          Predicate notDeleted = cb.isNull(root.get("deletedAt"));
          Predicate notBanned = cb.isFalse(root.get("isSystemBan"));

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
          Predicate finalPredicate = cb.and(authorInNetwork, notDeleted, notBanned, accessControl);

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

          if (isSelf) return cb.and(authorMatch, notDeleted, notBanned); // Xem full bài của mình

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
  public void deletePost(Long postId) {
    Long currentUserId = getCurrentUserId();
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (!post.getAuthor().getId().equals(currentUserId) && !hasRole("ADMIN")) {
      throw new AccessDeniedException("You are not authorized to delete this post.");
    }

    // Soft Delete (Thay vì xóa vật lý, set deletedAt để giữ lịch sử)
    post.setDeletedAt(Instant.now());
    postRepository.save(post);

    // Optional: Bắn event ra Kafka để các service khác biết bài này đã bị xóa
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
    List<PostResponse> content =
        postPage.getContent().stream().map(post -> toDtoWithDetails(post, currentUserId)).toList();

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

    // TODO: Chỗ này sau này bạn tích hợp Redis (ReactService) để lấy tổng Like
    // dto.setReactSummary(reactService.getReactSummaryFromRedis(post.getId(), currentUserId));

    if (post.getSharedPost() != null) {
      try {
        // Kiểm tra xem viewer có quyền xem bài gốc không
        checkViewPermission(post.getSharedPost(), currentUserId);
        // Đệ quy ánh xạ bài share
        dto.setSharedPost(postMapper.toDto(post.getSharedPost()));
      } catch (AccessDeniedException e) {
        dto.setSharedPost(null); // Không có quyền xem bài gốc
        // dto.setSharedPostVisible(false); // (Tuỳ logic FE của bạn)
      }
    }
    return dto;
  }
}
