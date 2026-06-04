package com.socialnetwork.media_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.client.UserServiceClient;
import com.socialnetwork.media_service.dto.comment.CommentRequest;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.comment.UpdateCommentRequest;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.enums.AccessScope;
import events.ContentCreatedEvent;
import com.socialnetwork.media_service.mapper.CommentMapper;
import com.socialnetwork.media_service.model.Comment;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.CommentRepository;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.ReactService;
import com.socialnetwork.media_service.service.StorageService;
import exception.AccessDeniedException;
import exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;
import vo.TargetType;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final UserCacheRepository userCacheRepository;
  private final UserServiceClient userServiceClient;
  private final StorageService storageService;
  private final ReactService reactService;
  private final CommentMapper commentMapper;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public CommentResponse createComment(CommentRequest request) {
    Long currentUserId = getCurrentUserId();
    UserCache author =
        userCacheRepository
            .findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User cache not found"));

    Post post =
        postRepository
            .findById(request.getPostId())
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    checkViewPermission(currentUserId, post);

    Comment parent = null;
    if (request.getParentId() != null) {
      parent =
          commentRepository
              .findById(request.getParentId())
              .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
    }

    List<Map<String, String>> mediaList = List.of();
    if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
      String url = storageService.saveFile(request.getMediaFile(), "comments");
      String type =
          isVideo(getExtension(request.getMediaFile().getOriginalFilename())) ? "video" : "image";
      mediaList = List.of(Map.of("url", url, "type", type));
    }

    Comment comment =
        Comment.builder()
            .content(request.getContent())
            .media(mediaList)
            .post(post)
            .author(author)
            .parent(parent)
            .reactCount(0)
            .build();

    Comment saved = commentRepository.save(comment);

    // Tăng count của bài Post
    postRepository.updateCommentCount(post.getId(), 1);

    // Bắn sự kiện ra Kafka (Cho AI Moderation hoặc Notification)
    try {
        ContentCreatedEvent event = new ContentCreatedEvent(
            saved.getId(), "COMMENT", saved.getContent(), author.getId(), saved.getMedia());
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("content-created-topic", payload);
    } catch (Exception e) {
        log.error("Failed to send content-created event for comment", e);
    }

    // Bắn notification event
    Long notificationReceiverId =
        parent != null ? parent.getAuthor().getId() : post.getAuthor().getId();

    if (!notificationReceiverId.equals(currentUserId)) {
      String notificationType = parent != null ? "REPLY_COMMENT" : "COMMENT_POST";
      Long postId = post.getId();
      kafkaTemplate.send(
          "notification-topic",
          new events.NotificationEvent(
              currentUserId, notificationReceiverId, notificationType, saved.getId(), postId));
      log.info(
          "Sent notification event: {} from {} to {}",
          notificationType,
          currentUserId,
          notificationReceiverId);
    }

    return commentMapper.toDto(saved);
  }

  @Override
  @Transactional
  public CommentResponse updateComment(UpdateCommentRequest request) {
    Long currentUserId = getCurrentUserId();
    Comment comment =
        commentRepository
            .findById(request.getCommentId())
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(currentUserId) && !hasRole("ADMIN")) {
      throw new AccessDeniedException("You are not authorized to update this comment.");
    }

    if (request.getContent() != null) {
      comment.setContent(request.getContent());
    }

    if (Boolean.TRUE.equals(request.getRemoveMedia())
        || (request.getMediaFile() != null && !request.getMediaFile().isEmpty())) {
      // Xóa file cũ trên Minio
      if (comment.getMedia() != null && !comment.getMedia().isEmpty()) {
        comment.getMedia().forEach(m -> storageService.deleteFile(m.get("url")));
      }
      comment.setMedia(List.of());
    }

    if (request.getMediaFile() != null && !request.getMediaFile().isEmpty()) {
      String url = storageService.saveFile(request.getMediaFile(), "comments");
      String type =
          isVideo(getExtension(request.getMediaFile().getOriginalFilename())) ? "video" : "image";
      comment.setMedia(List.of(Map.of("url", url, "type", type)));
    }

    Comment saved = commentRepository.save(comment);
    return commentMapper.toDto(saved);
  }

  @Override
  @Transactional
  public void updateSystemBanStatus(Long commentId, boolean isBanned) {
    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    comment.setDeletedAt(isBanned ? Instant.now() : null);
    comment.setIsSystemBan(isBanned);
    commentRepository.save(comment);
    log.info("Đã cập nhật SystemBan = {} cho Comment ID: {}", isBanned, commentId);
  }

  @Override
  public CommentResponse getCommentById(Long id) {
    return null;
  }

  @Override
  @Transactional
  public void deleteComment(Long id) {
    Long currentUserId = getCurrentUserId();
    Comment comment =
        commentRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(currentUserId) && !hasRole("ADMIN")) {
      throw new AccessDeniedException("You are not authorized to delete this comment.");
    }

    commentRepository.delete(comment);
    // Giảm count của bài Post
    postRepository.updateCommentCount(comment.getPost().getId(), -1);
  }

  @Override
  public Long getCommentOwnerId(Long commentId) {
    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
    return comment.getAuthor().getId();
  }

  @Override
  public List<CommentResponse> getCommentsByIds(List<Long> ids) {
    List<Comment> comments = commentRepository.findAllById(ids);
    // Dùng mapper chuyển sang DTO
    return comments.stream().map(commentMapper::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
    Long currentUserId = getCurrentUserId();
    Post post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    checkViewPermission(currentUserId, post);

    Page<Comment> rootComments = commentRepository.findByPostAndParentIsNull(post, pageable);
    return buildPageVO(rootComments, 0);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable) {
    Long currentUserId = getCurrentUserId();

    Comment parent =
        commentRepository
            .findById(parentId)
            .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));

    Post post = parent.getPost();
    checkViewPermission(currentUserId, post);

    Page<Comment> replies = commentRepository.findByParent(parent, pageable);
    return buildPageVO(replies, 1);
  }

  // --- HELPER METHODS ---

  private Long getCurrentUserId() {
    return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  private boolean hasRole(String role) {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role) || a.getAuthority().equals(role));
  }

  private void checkViewPermission(Long viewerId, Post post) {
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

  private PageVO<CommentResponse> buildPageVO(Page<Comment> page, int depth) {
    Long currentUserId = getCurrentUserId();
    List<Comment> comments = page.getContent();

    if (comments.isEmpty()) {
      return PageVO.<CommentResponse>builder()
          .page(page.getNumber())
          .size(page.getSize())
          .totalElements(page.getTotalElements())
          .totalPages(page.getTotalPages())
          .numberOfElements(0)
          .content(List.of())
          .build();
    }

    List<Long> commentIds = comments.stream().map(Comment::getId).toList();

    Map<Long, ReactSummaryDto> reactMap =
        reactService.getReactSummaries(commentIds, currentUserId, TargetType.COMMENT);

    Map<Long, Integer> childrenCountMap = commentRepository.findChildrenCounts(commentIds);

    List<CommentResponse> content =
        comments.stream()
            .map(
                comment -> {
                  CommentResponse dto = commentMapper.toDto(comment);

                  dto.setReactSummary(
                      reactMap.getOrDefault(
                          comment.getId(), new ReactSummaryDto(Map.of(), 0L, null)));

                  dto.setChildrenCount(childrenCountMap.getOrDefault(comment.getId(), 0));

                  dto.setDepth(depth);

                  return dto;
                })
            .toList();

    return PageVO.<CommentResponse>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  private String getExtension(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf(".");
    return dot != -1 ? filename.substring(dot + 1).toLowerCase() : "";
  }

  private boolean isVideo(String ext) {
    return List.of("mp4", "webm", "ogg", "mov", "quicktime").contains(ext.toLowerCase());
  }
}
