package com.socialnetwork.moderation_service.service.impl;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.dto.IdCount;
import com.socialnetwork.common.dto.MessageModerationView;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.events.MessageCreatedEvent;
import com.socialnetwork.common.events.ModerationActionEvent;
import com.socialnetwork.common.events.UserModerationEvent;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.AccountStatus;
import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.client.AiServiceClient;
import com.socialnetwork.moderation_service.client.AuthClient;
import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
import com.socialnetwork.moderation_service.dto.ModerationLogResponse;
import com.socialnetwork.moderation_service.dto.ModerationMessageResponse;
import com.socialnetwork.moderation_service.dto.ModerationUserDetailResponse;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.dto.UserModerationResponse;
import com.socialnetwork.moderation_service.dto.external.AuthExternalDto;
import com.socialnetwork.moderation_service.dto.external.CommentResponse;
import com.socialnetwork.moderation_service.dto.external.PostResponse;
import com.socialnetwork.moderation_service.dto.external.UserExternalDto;
import com.socialnetwork.moderation_service.enums.ModerationLogAction;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.ModerationLog;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ModerationLogRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import com.socialnetwork.moderation_service.service.KafkaEventPublisher;
import com.socialnetwork.moderation_service.service.ModerationService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

  private static final String SYSTEM_ACTOR = "System (AI)";

  private final UserClient userClient;
  private final MediaClient mediaClient;
  private final ChatClient chatClient;
  private final AuthClient authClient;
  private final AiServiceClient aiServiceClient;

  private final ReportRepository reportRepository;
  private final ComplaintRepository complaintRepository;
  private final ModerationLogRepository moderationLogRepository;

  private final ReportMapper reportMapper;
  private final KafkaEventPublisher eventPublisher;

  // ── Admin composition API ────────────────────────────────────────────────

  @Override
  public ModerationUserDetailResponse getUserDetailForAdmin(Long userId) {
    UserExternalDto profile = userClient.getUserDetail(userId);
    AuthExternalDto credential = authClient.getCredential(userId);

    return ModerationUserDetailResponse.builder()
        .id(profile.getId())
        .displayName(profile.getDisplayName())
        .avatarUrl(profile.getAvatarUrl())
        .email(credential.getEmail())
        .status(credential.getStatus())
        .bio(profile.getBio())
        .violationCount(reportRepository.countByTargetUserId(userId))
        .createdAt(profile.getCreatedAt())
        .lastActiveAt(profile.getLastActiveAt())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable) {
    return PageVO.from(reportRepository.findByTargetUserId(userId, pageable), reportMapper::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public ModerationMessageResponse getMessageDetailForAdmin(String messageId) {
    MessageModerationView view = chatClient.getMessage(messageId);
    Map<Long, UserSummary> senders = loadSenders(List.of(view));
    ModerationMessageResponse response = toMessageResponse(view, senders);
    applyCounts(List.of(response), TargetType.MESSAGE);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter) {
    Page<IdCount> reported = reportRepository.findTopReportedUsers(pageable);
    if (reported.isEmpty()) {
      return PageVO.emptyPage(reported);
    }

    List<Long> userIds = reported.getContent().stream().map(ModerationServiceImpl::toLong).toList();

    Map<Long, UserExternalDto> profiles =
        userClient.getUsersByIds(userIds).stream()
            .collect(Collectors.toMap(UserExternalDto::getId, Function.identity()));
    Map<Long, AuthExternalDto> credentials =
        authClient.getCredentialsByIds(userIds).stream()
            .collect(Collectors.toMap(AuthExternalDto::getId, Function.identity()));

    List<UserModerationResponse> content =
        reported.getContent().stream()
            .map(
                idCount -> {
                  Long userId = toLong(idCount);
                  UserExternalDto profile = profiles.get(userId);
                  AuthExternalDto credential = credentials.get(userId);
                  if (profile == null || credential == null) {
                    return null;
                  }
                  return UserModerationResponse.builder()
                      .userId(userId)
                      .username(credential.getUsername())
                      .email(credential.getEmail())
                      .displayName(profile.getDisplayName())
                      .avatar(profile.getAvatarUrl())
                      .status(credential.getStatus())
                      .violationCount(idCount.getCount() == null ? 0L : idCount.getCount())
                      .build();
                })
            .filter(Objects::nonNull)
            .toList();

    return pageOf(reported, content);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<PostResponse> getFlaggedPosts(String filter, Pageable pageable) {
    Page<IdCount> flagged = reportRepository.findTopReportedTargets(TargetType.POST, pageable);
    if (flagged.isEmpty()) {
      return PageVO.emptyPage(flagged);
    }

    List<PostResponse> posts =
        mediaClient.getPostsByIds(flagged.getContent().stream().map(ModerationServiceImpl::toLong).toList());
    enrichWithCounts(
        posts,
        PostResponse::getId,
        PostResponse::setReportCount,
        PostResponse::setComplaintCount,
        TargetType.POST);
    return pageOf(flagged, posts);
  }

  @Override
  @Transactional(readOnly = true)
  public PostResponse getPostDetailForAdmin(Long postId) {
    List<PostResponse> posts = mediaClient.getPostsByIds(List.of(postId));
    if (posts.isEmpty()) {
      throw new ResourceNotFoundException("The post does not exist or has been deleted");
    }
    PostResponse post = posts.getFirst();
    enrichWithCounts(
        List.of(post),
        PostResponse::getId,
        PostResponse::setReportCount,
        PostResponse::setComplaintCount,
        TargetType.POST);
    return post;
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<CommentResponse> getFlaggedComments(String filter, Pageable pageable) {
    Page<IdCount> flagged = reportRepository.findTopReportedTargets(TargetType.COMMENT, pageable);
    if (flagged.isEmpty()) {
      return PageVO.emptyPage(flagged);
    }

    List<CommentResponse> comments =
        mediaClient.getCommentsByIds(
            flagged.getContent().stream().map(ModerationServiceImpl::toLong).toList());
    enrichWithCounts(
        comments,
        CommentResponse::getId,
        CommentResponse::setReportCount,
        CommentResponse::setComplaintCount,
        TargetType.COMMENT);
    return pageOf(flagged, comments);
  }

  @Override
  @Transactional(readOnly = true)
  public CommentResponse getCommentDetailForAdmin(Long commentId) {
    List<CommentResponse> comments = mediaClient.getCommentsByIds(List.of(commentId));
    if (comments.isEmpty()) {
      throw new ResourceNotFoundException("The comment does not exist or has been deleted");
    }
    CommentResponse comment = comments.getFirst();
    enrichWithCounts(
        List.of(comment),
        CommentResponse::getId,
        CommentResponse::setReportCount,
        CommentResponse::setComplaintCount,
        TargetType.COMMENT);
    return comment;
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable) {
    Page<IdCount> flagged = reportRepository.findTopReportedTargets(TargetType.MESSAGE, pageable);
    if (flagged.isEmpty()) {
      return PageVO.emptyPage(flagged);
    }

    List<MessageModerationView> views =
        chatClient.getMessagesByIds(flagged.getContent().stream().map(IdCount::getId).toList());
    Map<Long, UserSummary> senders = loadSenders(views);

    List<ModerationMessageResponse> messages =
        views.stream().map(view -> toMessageResponse(view, senders)).toList();
    applyCounts(messages, TargetType.MESSAGE);
    return pageOf(flagged, messages);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable) {
    Specification<ModerationLog> spec =
        (filter == null || filter.isBlank())
            ? (root, query, cb) -> cb.conjunction()
            : RSQLJPASupport.toSpecification(filter);

    return PageVO.from(moderationLogRepository.findAll(spec, pageable), this::toLogResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ModerationLogResponse> getHistory(
      TargetType type, String id, Pageable pageable, String filter) {
    return PageVO.from(
        moderationLogRepository.findHistory(type, id, filter, pageable), this::toLogResponse);
  }

  // ── Moderation actions ───────────────────────────────────────────────────

  @Override
  @Transactional
  public void updateUserStatus(Long targetUserId, AccountStatus newStatus, String reason) {
    Long adminId = SecurityUtils.getCurrentUserId();
    if (adminId.equals(targetUserId)) {
      throw new BadRequestException("You cannot block or unblock your own account.");
    }

    ModerationLogAction action =
        newStatus == AccountStatus.BLOCKED
            ? ModerationLogAction.USER_BLOCK
            : ModerationLogAction.USER_UNBLOCK;

    saveLog(adminId, TargetType.USER, targetUserId.toString(), action, reason, null, null);
    eventPublisher.publishAfterCommit(
        KafkaTopics.USER_MODERATION_ACTIONS,
        targetUserId.toString(),
        new UserModerationEvent(targetUserId, newStatus, reason));
    log.info("User {} set to {} by admin {}", targetUserId, newStatus, adminId);
  }

  @Override
  @Transactional
  public void blockContent(String targetId, TargetType targetType, Long reportId) {
    Long adminId = SecurityUtils.findCurrentUserId().orElse(null);
    String reason = "Blocked by a moderator";

    saveLog(
        adminId, targetType, targetId, ModerationLogAction.ADMIN_BLOCK, reason, reportId, null);
    publishContentAction(targetId, targetType, ModerationAction.BLOCK, reason);
    log.info("{} {} blocked by admin {}", targetType, targetId, adminId);
  }

  @Override
  @Transactional
  public void unblockContent(String targetId, TargetType targetType, Long complaintId) {
    Long adminId = SecurityUtils.findCurrentUserId().orElse(null);
    String reason = "Restored by a moderator";

    saveLog(
        adminId, targetType, targetId, ModerationLogAction.ADMIN_UNBLOCK, reason, null, complaintId);
    publishContentAction(targetId, targetType, ModerationAction.UNBLOCK, reason);
    log.info("{} {} restored by admin {}", targetType, targetId, adminId);
  }

  // ── AI moderation pipeline ───────────────────────────────────────────────

  @Override
  @Transactional
  public void moderateContent(ContentCreatedEvent event) {
    String targetId = String.valueOf(event.targetId());
    AiModerationResponse verdict =
        scan(AiModerationRequest.builder().text(event.content()).media(event.media()).build());

    if (!verdict.isToxic()) {
      log.debug("{} {} passed moderation", event.targetType(), targetId);
      return;
    }
    autoBan(targetId, event.targetType(), event.authorId(), verdict.getReason());
  }

  @Override
  @Transactional
  public void moderateMessage(MessageCreatedEvent event) {
    AiModerationResponse verdict =
        scan(AiModerationRequest.builder().text(event.content()).build());

    if (!verdict.isToxic()) {
      log.debug("Message {} passed moderation", event.messageId());
      return;
    }
    autoBan(event.messageId(), TargetType.MESSAGE, event.senderId(), verdict.getReason());
  }

  /**
   * Calls the model. Any error propagates on purpose: a scan that did not happen is not a clean
   * verdict, so the record is retried and eventually parked in the dead-letter topic.
   */
  private AiModerationResponse scan(AiModerationRequest request) {
    return aiServiceClient.checkToxicity(request);
  }

  /**
   * Records the automatic decision: one system report, one {@code AUTO_BAN} log row and one BLOCK
   * command. Re-delivered events are ignored, so nothing is written twice.
   */
  private void autoBan(String targetId, TargetType targetType, Long authorId, String aiReason) {
    if (reportRepository.existsByTargetTypeAndTargetIdAndSource(
        targetType, targetId, ReportSource.SYSTEM)) {
      log.debug("{} {} was already auto-moderated, skipping", targetType, targetId);
      return;
    }

    String reason = "AI detected a violation: " + aiReason;
    Report report =
        reportRepository.save(
            Report.builder()
                .source(ReportSource.SYSTEM)
                .status(ReportStatus.APPROVED)
                .targetType(targetType)
                .targetId(targetId)
                .targetUserId(authorId)
                .reason(ReportReason.AI_DETECTED)
                .customReason(reason)
                .bannedBySystem(true)
                .build());

    saveLog(null, targetType, targetId, ModerationLogAction.AUTO_BAN, reason, report.getId(), null);
    publishContentAction(targetId, targetType, ModerationAction.BLOCK, reason);
    log.warn("Auto-banned {} {}: {}", targetType, targetId, aiReason);
  }

  private void publishContentAction(
      String targetId, TargetType targetType, ModerationAction action, String reason) {
    eventPublisher.publishAfterCommit(
        KafkaTopics.MODERATION_ACTIONS,
        targetId,
        new ModerationActionEvent(targetId, targetType, action, reason));
  }

  private void saveLog(
      Long actorId,
      TargetType targetType,
      String targetId,
      ModerationLogAction action,
      String reason,
      Long reportId,
      Long complaintId) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(actorId)
            .targetType(targetType)
            .targetId(targetId)
            .action(action)
            .reason(reason)
            .reportId(reportId)
            .complaintId(complaintId)
            .build());
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private ModerationLogResponse toLogResponse(ModerationLog entry) {
    return ModerationLogResponse.builder()
        .id(entry.getId())
        .targetType(entry.getTargetType())
        .targetId(entry.getTargetId())
        .action(entry.getAction())
        .reason(entry.getReason())
        .actorId(entry.getActorId())
        .actorName(entry.getActorId() != null ? "Admin " + entry.getActorId() : SYSTEM_ACTOR)
        .reportId(entry.getReportId())
        .complaintId(entry.getComplaintId())
        .createdAt(entry.getCreatedAt())
        .build();
  }

  private Map<Long, UserSummary> loadSenders(List<MessageModerationView> views) {
    List<Long> senderIds =
        views.stream()
            .map(MessageModerationView::senderId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (senderIds.isEmpty()) {
      return Map.of();
    }
    return userClient.getSummaries(senderIds).stream()
        .collect(Collectors.toMap(UserSummary::id, Function.identity()));
  }

  private ModerationMessageResponse toMessageResponse(
      MessageModerationView view, Map<Long, UserSummary> senders) {
    UserSummary sender = senders.get(view.senderId());
    return ModerationMessageResponse.builder()
        .id(view.id())
        .conversationId(view.roomId())
        .senderId(view.senderId())
        .senderName(sender != null ? sender.displayName() : null)
        .senderAvatar(sender != null ? sender.avatarUrl() : null)
        .content(view.content())
        .sentAt(view.createdAt())
        .isSystemBan(view.systemBanned())
        .build();
  }

  private void applyCounts(List<ModerationMessageResponse> messages, TargetType type) {
    enrichWithCounts(
        messages,
        ModerationMessageResponse::getId,
        ModerationMessageResponse::setReportCount,
        ModerationMessageResponse::setComplaintCount,
        type);
  }

  private <T> void enrichWithCounts(
      List<T> responses,
      Function<T, Object> idExtractor,
      BiConsumer<T, Long> setReportCount,
      BiConsumer<T, Long> setComplaintCount,
      TargetType type) {
    if (responses.isEmpty()) {
      return;
    }
    List<String> ids = responses.stream().map(idExtractor).map(String::valueOf).toList();

    Map<String, Long> reports = new HashMap<>();
    reportRepository
        .countByTargetTypeAndTargetIdIn(type, ids)
        .forEach(item -> reports.put(item.getId(), item.getCount()));

    Map<String, Long> complaints = new HashMap<>();
    complaintRepository
        .countByTargetTypeAndTargetIdIn(type, ids)
        .forEach(item -> complaints.put(item.getId(), item.getCount()));

    for (T response : responses) {
      String id = String.valueOf(idExtractor.apply(response));
      setReportCount.accept(response, reports.getOrDefault(id, 0L));
      setComplaintCount.accept(response, complaints.getOrDefault(id, 0L));
    }
  }

  private static Long toLong(IdCount idCount) {
    return Long.valueOf(idCount.getId());
  }

  private static <T> PageVO<T> pageOf(Page<?> page, List<T> content) {
    return PageVO.<T>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }
}
