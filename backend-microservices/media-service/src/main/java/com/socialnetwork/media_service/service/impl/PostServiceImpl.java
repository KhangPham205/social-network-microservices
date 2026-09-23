package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.client.UserServiceClient;
import com.socialnetwork.media_service.dto.post.PostResponse;
import com.socialnetwork.media_service.dto.post.UpdatePostRequest;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.enums.AccessScope;
import com.socialnetwork.media_service.mapper.PostMapper;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.ContentAccessService;
import com.socialnetwork.media_service.service.KafkaEventPublisher;
import com.socialnetwork.media_service.service.PostService;
import com.socialnetwork.media_service.service.ReactService;
import com.socialnetwork.media_service.service.RecommendationService;
import com.socialnetwork.media_service.service.StorageService;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

  private static final String ADMIN = "ADMIN";
  private static final List<String> VIDEO_EXTENSIONS =
      List.of("mp4", "webm", "ogg", "mov", "quicktime");

  private final PostRepository postRepository;
  private final UserCacheRepository userCacheRepository;
  private final UserServiceClient userServiceClient;
  private final StorageService storageService;
  private final RecommendationService recommendationService;
  private final ReactService reactService;
  private final ContentAccessService contentAccessService;
  private final KafkaEventPublisher eventPublisher;
  private final PostMapper postMapper;

  @Override
  @Transactional
  public PostResponse create(
      String content, String accessModifier, List<MultipartFile> mediaFiles) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    UserCache author = requireUserCache(currentUserId);

    Post post =
        Post.builder()
            .author(author)
            .content(content)
            .media(processMediaUploads(mediaFiles))
            .accessModifier(parseAccessScope(accessModifier))
            .isSystemBan(false)
            .build();

    Post savedPost = postRepository.save(post);

    eventPublisher.publishAfterCommit(
        KafkaTopics.CONTENT_CREATED,
        String.valueOf(savedPost.getId()),
        new ContentCreatedEvent(
            savedPost.getId(),
            TargetType.POST,
            savedPost.getContent(),
            author.getId(),
            savedPost.getMedia()));

    return toDtoWithDetails(savedPost, currentUserId);
  }

  @Override
  @Transactional
  public PostResponse update(UpdatePostRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(request.getPostId())
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (!post.getAuthor().getId().equals(currentUserId) && !SecurityUtils.hasRole(ADMIN)) {
      throw new AccessDeniedException("You are not authorized to update this post.");
    }

    if (request.getContent() != null) {
      post.setContent(request.getContent());
    }
    if (request.getAccessModifier() != null) {
      post.setAccessModifier(parseAccessScope(request.getAccessModifier()));
    }

    List<Map<String, String>> existingMedia =
        post.getMedia() != null ? post.getMedia() : List.of();
    Set<String> ownedUrls = new HashSet<>();
    existingMedia.forEach(m -> ownedUrls.add(m.get("url")));

    List<Map<String, String>> currentMedia = new ArrayList<>();
    if (request.getKeepMediaUrls() != null) {
      currentMedia.addAll(
          existingMedia.stream()
              .filter(m -> request.getKeepMediaUrls().contains(m.get("url")))
              .toList());
    }

    // Only objects that really belong to this post may be removed from storage.
    if (request.getRemoveMediaUrls() != null) {
      request.getRemoveMediaUrls().stream()
          .filter(ownedUrls::contains)
          .forEach(storageService::deleteFile);
    }

    if (request.getMediaFiles() != null && !request.getMediaFiles().isEmpty()) {
      currentMedia.addAll(processMediaUploads(request.getMediaFiles()));
    }

    post.setMedia(currentMedia);
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

    post.setIsSystemBan(isBanned);
    postRepository.save(post);
    log.info("System ban set to {} for post {}", isBanned, postId);
  }

  @Override
  @Transactional(readOnly = true)
  public PostResponse getPostById(Long postId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Post post = contentAccessService.requireVisiblePost(postId, currentUserId);
    return toDtoWithDetails(post, currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<PostResponse> getFeed(Pageable pageable, String filter) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    List<Long> networkIds = networkIdsOf(currentUserId);
    List<Long> recommendedPostIds = recommendedPostIds(currentUserId, filter);

    if (recommendedPostIds.isEmpty()) {
      // AI service, Milvus or Neo4j is unavailable: serve the newest network posts instead.
      log.debug("Recommendation ranking unavailable, serving the chronological network feed");
      Page<Post> page =
          postRepository.findAll(networkFeedSpec(currentUserId, networkIds, null), byNewest(pageable));
      return buildPageVO(page, currentUserId);
    }

    Page<Post> page =
        postRepository.findAll(
            networkFeedSpec(currentUserId, networkIds, recommendedPostIds), pageable);

    List<Post> ranked =
        page.getContent().stream()
            .sorted(Comparator.comparingInt(p -> recommendedPostIds.indexOf(p.getId())))
            .toList();
    return buildPageVO(new PageImpl<>(ranked, pageable, page.getTotalElements()), currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<PostResponse> getUserPosts(Long targetUserId, Pageable pageable) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    boolean isSelf = currentUserId.equals(targetUserId);
    boolean isAdmin = SecurityUtils.hasRole(ADMIN);
    boolean isFriend = !isSelf && userServiceClient.isFriend(currentUserId, targetUserId);

    Specification<Post> spec =
        (root, query, cb) -> {
          Predicate authorMatch = cb.equal(authorId(root), targetUserId);
          Predicate notDeleted = cb.isNull(root.get("deletedAt"));

          if (isSelf || isAdmin) {
            return cb.and(authorMatch, notDeleted);
          }

          Predicate visibleScope =
              isFriend
                  ? cb.or(scopeIs(root, cb, AccessScope.PUBLIC), scopeIs(root, cb, AccessScope.FRIENDS))
                  : scopeIs(root, cb, AccessScope.PUBLIC);
          return cb.and(authorMatch, notDeleted, notBanned(root, cb), visibleScope);
        };

    return buildPageVO(postRepository.findAll(spec, byNewest(pageable)), currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<PostResponse> getMyPosts(Pageable pageable) {
    return getUserPosts(SecurityUtils.getCurrentUserId(), pageable);
  }

  @Override
  @Transactional
  public PostResponse sharePost(Long originalPostId, String caption, AccessScope accessScope) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    UserCache currentUser = requireUserCache(currentUserId);
    Post original = contentAccessService.requireVisiblePost(originalPostId, currentUserId);

    if (original.getAccessModifier() == AccessScope.PRIVATE) {
      throw new BadRequestException("Cannot share a private post.");
    }
    if (original.getSharedPost() != null) {
      // Always point at the original so the chain never grows.
      original = original.getSharedPost();
    }

    Post shared =
        Post.builder()
            .author(currentUser)
            .content(caption != null ? caption : "")
            .sharedPost(original)
            .accessModifier(accessScope != null ? accessScope : AccessScope.PUBLIC)
            .isSystemBan(false)
            .build();

    Post savedSharedPost = postRepository.save(shared);
    postRepository.updateShareCount(original.getId(), 1);
    return toDtoWithDetails(savedSharedPost, currentUserId);
  }

  @Override
  @Transactional
  public void deletePost(Long postId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (!post.getAuthor().getId().equals(currentUserId) && !SecurityUtils.hasRole(ADMIN)) {
      throw new AccessDeniedException("You are not authorized to delete this post.");
    }

    // Soft delete: shares and comments keep referencing this row.
    post.setDeletedAt(Instant.now());
    postRepository.save(post);
    if (post.getSharedPost() != null) {
      postRepository.updateShareCount(post.getSharedPost().getId(), -1);
    }
    log.info("Post {} soft deleted by user {}", postId, currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public Long getPostOwnerId(Long postId) {
    return postRepository
        .findById(postId)
        .map(post -> post.getAuthor().getId())
        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PostResponse> getPostsByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return postRepository.findByIdInAndDeletedAtIsNull(ids).stream()
        .map(postMapper::toDto)
        .toList();
  }

  // ---------------------------------------------------------------- specifications

  private static Path<Long> authorId(Root<Post> root) {
    return root.get("author").get("id");
  }

  private static Predicate scopeIs(
      Root<Post> root, jakarta.persistence.criteria.CriteriaBuilder cb, AccessScope scope) {
    return cb.equal(root.get("accessModifier"), scope);
  }

  private static Predicate notBanned(
      Root<Post> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
    return cb.or(cb.isNull(root.get("isSystemBan")), cb.isFalse(root.get("isSystemBan")));
  }

  /** Posts of the viewer's network the viewer is allowed to see, optionally narrowed to ids. */
  private Specification<Post> networkFeedSpec(
      Long currentUserId, List<Long> networkIds, List<Long> onlyIds) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (onlyIds != null && !onlyIds.isEmpty()) {
        predicates.add(root.get("id").in(onlyIds));
      }
      predicates.add(authorId(root).in(networkIds));

      Predicate mine = cb.equal(authorId(root), currentUserId);
      Predicate publicScope = scopeIs(root, cb, AccessScope.PUBLIC);
      Predicate friendScope = scopeIs(root, cb, AccessScope.FRIENDS);
      Predicate visibleToOthers =
          cb.and(
              cb.isNull(root.get("deletedAt")),
              notBanned(root, cb),
              cb.or(publicScope, friendScope));

      predicates.add(cb.or(cb.and(mine, cb.isNull(root.get("deletedAt"))), visibleToOthers));
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Pageable byNewest(Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      return pageable;
    }
    return PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  // ---------------------------------------------------------------- helpers

  private UserCache requireUserCache(Long userId) {
    return userCacheRepository
        .findById(userId)
        .orElseThrow(
            () -> new ResourceNotFoundException("User cache not found. Please sync user data."));
  }

  private static AccessScope parseAccessScope(String value) {
    if (value == null || value.isBlank()) {
      return AccessScope.PUBLIC;
    }
    try {
      return AccessScope.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Unknown access scope: " + value);
    }
  }

  /** Friends plus the viewer; user-service being down only narrows the feed to the viewer. */
  private List<Long> networkIdsOf(Long currentUserId) {
    List<Long> networkIds = new ArrayList<>();
    try {
      networkIds.addAll(userServiceClient.getNetworkIds(currentUserId));
    } catch (Exception e) {
      log.warn("user-service network lookup failed, feed limited to own posts: {}", e.toString());
    }
    if (!networkIds.contains(currentUserId)) {
      networkIds.add(currentUserId);
    }
    return networkIds;
  }

  private List<Long> recommendedPostIds(Long currentUserId, String filter) {
    try {
      return recommendationService.getExploreFeed(currentUserId, filter);
    } catch (Exception e) {
      log.warn("Recommendation ranking failed, falling back to the network feed: {}", e.toString());
      return List.of();
    }
  }

  private List<Map<String, String>> processMediaUploads(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      return new ArrayList<>();
    }
    return files.stream()
        .filter(file -> file != null && !file.isEmpty())
        .map(
            file -> {
              String url = storageService.saveFile(file, "posts");
              String extension = getExtension(file.getOriginalFilename());
              return Map.of("type", isVideo(extension) ? "video" : "image", "url", url);
            })
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  private static String getExtension(String filename) {
    if (filename == null) {
      return "";
    }
    int dot = filename.lastIndexOf('.');
    return dot != -1 ? filename.substring(dot + 1).toLowerCase() : "";
  }

  private static boolean isVideo(String extension) {
    return VIDEO_EXTENSIONS.contains(extension);
  }

  private PageVO<PostResponse> buildPageVO(Page<Post> postPage, Long currentUserId) {
    List<Post> posts = postPage.getContent();
    if (posts.isEmpty()) {
      return PageVO.emptyPage(postPage);
    }

    List<Long> postIds = posts.stream().map(Post::getId).toList();
    List<Long> sharedPostIds =
        posts.stream().map(Post::getSharedPost).filter(Objects::nonNull).map(Post::getId).toList();

    Map<Long, ReactSummaryDto> reactMap =
        reactService.getReactSummaries(postIds, currentUserId, TargetType.POST);
    Map<Long, ReactSummaryDto> sharedReactMap =
        sharedPostIds.isEmpty()
            ? Map.of()
            : reactService.getReactSummaries(sharedPostIds, currentUserId, TargetType.POST);

    List<PostResponse> content =
        posts.stream()
            .map(
                post -> {
                  PostResponse dto = postMapper.toDto(post);
                  dto.setReactSummary(reactMap.getOrDefault(post.getId(), emptySummary()));
                  dto.setSharedPost(
                      sharedPostDto(
                          post.getSharedPost(),
                          currentUserId,
                          shared -> sharedReactMap.getOrDefault(shared.getId(), emptySummary())));
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
    dto.setReactSummary(reactService.getReactSummary(post.getId(), TargetType.POST, currentUserId));
    dto.setSharedPost(
        sharedPostDto(
            post.getSharedPost(),
            currentUserId,
            shared -> reactService.getReactSummary(shared.getId(), TargetType.POST, currentUserId)));
    return dto;
  }

  /** Renders the original post of a share, or {@code null} when the viewer may not see it. */
  private PostResponse sharedPostDto(
      Post sharedPost,
      Long currentUserId,
      java.util.function.Function<Post, ReactSummaryDto> reactLookup) {
    if (sharedPost == null || !contentAccessService.canView(sharedPost, currentUserId)) {
      return null;
    }
    PostResponse dto = postMapper.toDto(sharedPost);
    dto.setSharedPost(null);
    dto.setReactSummary(reactLookup.apply(sharedPost));
    return dto;
  }

  private static ReactSummaryDto emptySummary() {
    return new ReactSummaryDto(Map.of(), 0L, null);
  }
}
