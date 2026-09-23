package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.NotificationType;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.dto.comment.CommentRequest;
import com.socialnetwork.media_service.dto.comment.CommentResponse;
import com.socialnetwork.media_service.dto.comment.UpdateCommentRequest;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.mapper.CommentMapper;
import com.socialnetwork.media_service.model.Comment;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.CommentRepository;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.ContentAccessService;
import com.socialnetwork.media_service.service.KafkaEventPublisher;
import com.socialnetwork.media_service.service.ReactService;
import com.socialnetwork.media_service.service.StorageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

  private static final String ADMIN = "ADMIN";
  private static final List<String> VIDEO_EXTENSIONS =
      List.of("mp4", "webm", "ogg", "mov", "quicktime");

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final UserCacheRepository userCacheRepository;
  private final StorageService storageService;
  private final ReactService reactService;
  private final ContentAccessService contentAccessService;
  private final KafkaEventPublisher eventPublisher;
  private final CommentMapper commentMapper;

  @Override
  @Transactional
  public CommentResponse createComment(CommentRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    UserCache author =
        userCacheRepository
            .findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User cache not found"));

    Post post = contentAccessService.requireVisiblePost(request.getPostId(), currentUserId);

    Comment parent = null;
    if (request.getParentId() != null) {
      parent =
          commentRepository
              .findByIdAndDeletedAtIsNull(request.getParentId())
              .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
      if (!parent.getPost().getId().equals(post.getId())) {
        throw new BadRequestException("Parent comment belongs to another post");
      }
    }

    Comment comment =
        Comment.builder()
            .content(request.getContent())
            .media(uploadMedia(request.getMediaFile()))
            .post(post)
            .author(author)
            .parent(parent)
            .reactCount(0)
            .isSystemBan(false)
            .build();

    Comment saved = commentRepository.save(comment);
    postRepository.updateCommentCount(post.getId(), 1);

    eventPublisher.publishAfterCommit(
        KafkaTopics.CONTENT_CREATED,
        String.valueOf(saved.getId()),
        new ContentCreatedEvent(
            saved.getId(),
            TargetType.COMMENT,
            saved.getContent(),
            author.getId(),
            saved.getMedia()));

    Long receiverId = parent != null ? parent.getAuthor().getId() : post.getAuthor().getId();
    if (!receiverId.equals(currentUserId)) {
      NotificationType type =
          parent != null ? NotificationType.REPLY_COMMENT : NotificationType.COMMENT_POST;
      eventPublisher.publishAfterCommit(
          KafkaTopics.NOTIFICATION,
          String.valueOf(receiverId),
          NotificationEvent.of(currentUserId, receiverId, type, saved.getId(), post.getId()));
    }

    return commentMapper.toDto(saved);
  }

  @Override
  @Transactional
  public CommentResponse updateComment(UpdateCommentRequest request) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Comment comment =
        commentRepository
            .findByIdAndDeletedAtIsNull(request.getCommentId())
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(currentUserId) && !SecurityUtils.hasRole(ADMIN)) {
      throw new AccessDeniedException("You are not authorized to update this comment.");
    }

    if (request.getContent() != null) {
      comment.setContent(request.getContent());
    }

    boolean replacingMedia = request.getMediaFile() != null && !request.getMediaFile().isEmpty();
    if (Boolean.TRUE.equals(request.getRemoveMedia()) || replacingMedia) {
      // Only the objects this comment owns are removed from storage.
      if (comment.getMedia() != null) {
        comment.getMedia().stream()
            .map(media -> media.get("url"))
            .filter(java.util.Objects::nonNull)
            .forEach(storageService::deleteFile);
      }
      comment.setMedia(List.of());
    }
    if (replacingMedia) {
      comment.setMedia(uploadMedia(request.getMediaFile()));
    }

    return commentMapper.toDto(commentRepository.save(comment));
  }

  @Override
  @Transactional
  public void updateSystemBanStatus(Long commentId, boolean isBanned) {
    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    comment.setIsSystemBan(isBanned);
    commentRepository.save(comment);
    log.info("System ban set to {} for comment {}", isBanned, commentId);
  }

  @Override
  @Transactional
  public void deleteComment(Long id) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Comment comment =
        commentRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(currentUserId) && !SecurityUtils.hasRole(ADMIN)) {
      throw new AccessDeniedException("You are not authorized to delete this comment.");
    }

    // Soft delete: replies and reactions keep their foreign keys.
    comment.setDeletedAt(Instant.now());
    commentRepository.save(comment);
    postRepository.updateCommentCount(comment.getPost().getId(), -1);
    log.info("Comment {} soft deleted by user {}", id, currentUserId);
  }

  @Override
  @Transactional(readOnly = true)
  public Long getCommentOwnerId(Long commentId) {
    return commentRepository
        .findById(commentId)
        .map(comment -> comment.getAuthor().getId())
        .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommentResponse> getCommentsByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return commentRepository.findByIdInAndDeletedAtIsNull(ids).stream()
        .map(commentMapper::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Post post = contentAccessService.requireVisiblePost(postId, currentUserId);

    Page<Comment> rootComments =
        commentRepository.findByPostAndParentIsNullAndDeletedAtIsNull(post, pageable);
    return buildPageVO(rootComments, currentUserId, 0);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<CommentResponse> getReplies(Long parentId, Pageable pageable) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    Comment parent = contentAccessService.requireVisibleComment(parentId, currentUserId);

    Page<Comment> replies = commentRepository.findByParentAndDeletedAtIsNull(parent, pageable);
    return buildPageVO(replies, currentUserId, 1);
  }

  // ---------------------------------------------------------------- helpers

  private List<Map<String, String>> uploadMedia(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return List.of();
    }
    String url = storageService.saveFile(file, "comments");
    String type = isVideo(getExtension(file.getOriginalFilename())) ? "video" : "image";
    return List.of(Map.of("url", url, "type", type));
  }

  private PageVO<CommentResponse> buildPageVO(Page<Comment> page, Long currentUserId, int depth) {
    List<Comment> comments = page.getContent();
    if (comments.isEmpty()) {
      return PageVO.emptyPage(page);
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
}
