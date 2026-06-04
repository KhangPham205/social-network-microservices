package com.socialnetwork.moderation_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.moderation_service.client.AuthClient;
import com.socialnetwork.moderation_service.client.ChatClient;
import com.socialnetwork.moderation_service.client.MediaClient;
import com.socialnetwork.moderation_service.client.UserClient;
import com.socialnetwork.moderation_service.dto.*;
import com.socialnetwork.moderation_service.dto.external.*;
import com.socialnetwork.moderation_service.enums.AccountStatus;
import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.event.ContentModerationEvent;
import com.socialnetwork.moderation_service.event.UserModerationEvent;
import com.socialnetwork.moderation_service.mapper.ReportMapper;
import com.socialnetwork.moderation_service.model.ModerationLog;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ComplaintRepository;
import com.socialnetwork.moderation_service.repository.ModerationLogRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import dto.IdCount;
import exception.BadRequestException;
import exception.ResourceNotFoundException;
import io.github.perplexhub.rsql.RSQLJPASupport;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
import vo.PageVO;
import vo.TargetType;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImpl implements ModerationService {

  private final ObjectMapper objectMapper;
  private final UserClient userClient;
  private final MediaClient mediaClient;
  private final ChatClient chatClient;
  private final AuthClient authClient;
  private final ReportRepository reportRepository;
  private final ComplaintRepository complaintRepository;
  private final ReportMapper reportMapper;
  private final ModerationLogRepository moderationLogRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  private static final String MODERATION_ACTIONS_TOPIC = "moderation-actions";

  // Lấy ID Admin an toàn từ Security Context
  private Long getCurrentAdminIdSafely() {
    try {
      String name = SecurityContextHolder.getContext().getAuthentication().getName();
      return "anonymousUser".equals(name) ? null : Long.parseLong(name);
    } catch (Exception e) {
      return null; // System / Bot
    }
  }

  @Override
  public ModerationUserDetailResponse getUserDetailForAdmin(Long userId) {
    UserExternalDto userProfile = userClient.getUserDetail(userId);
    if (userProfile == null) throw new ResourceNotFoundException("User profile not found");

    AuthExternalDto authInfo = authClient.getCredential(userId);
    if (authInfo == null) throw new ResourceNotFoundException("User credential not found");

    long totalReports = reportRepository.countByTargetUserId(userId);

    return ModerationUserDetailResponse.builder()
        .id(userProfile.getId())
        .displayName(userProfile.getDisplayName())
        .avatarUrl(userProfile.getAvatarUrl())
        .email(authInfo.getEmail())
        .status(AccountStatus.valueOf(authInfo.getStatus()))
        .bio(userProfile.getBio())
        .violationCount(totalReports)
        .createdAt(userProfile.getCreatedAt())
        .lastActiveAt(userProfile.getLastActiveAt())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReportResponse> getUserViolations(Long userId, Pageable pageable) {
    // Có thể check xem user tồn tại không bằng userClient, nhưng để tiết kiệm call HTTP, ta cứ
    // query trực tiếp Report
    Page<Report> page = reportRepository.findByTargetUserId(userId, pageable);

    List<ReportResponse> content =
        page.getContent().stream().map(reportMapper::toResponse).toList();

    return buildPageVO(page, content);
  }

  @Override
  public ModerationMessageResponse getMessageDetailForAdmin(String messageId) {
    // Dùng ChatClient gọi thẳng sang Chat Service lấy detail
    ModerationMessageResponse response = chatClient.getMessageDetail(messageId);

    // Đắp thêm thông tin report ở DB của mình vào
    try {
      long reportCount =
          reportRepository
              .countByTargetTypeAndTargetIdIn(TargetType.MESSAGE, List.of(messageId))
              .stream()
              .findFirst()
              .map(IdCount::getCount)
              .orElse(0L);
      response.setReportCount(reportCount);
    } catch (Exception ignored) {
    }

    return response;
  }

  @Override
  public PageVO<UserModerationResponse> getUsersWithReportCount(Pageable pageable, String filter) {
    Page<IdCount> reportedUsersPage = reportRepository.findTopReportedUsers(pageable);
    if (reportedUsersPage.isEmpty()) return buildEmptyPageVO(reportedUsersPage);

    List<Long> userIds =
        reportedUsersPage.getContent().stream()
            .map(idCount -> Long.valueOf(String.valueOf(idCount.getId())))
            .toList();

    List<UserExternalDto> userProfiles = userClient.getUsersByIds(userIds);
    List<AuthExternalDto> authInfos = authClient.getCredentialsByIds(userIds);

    Map<Long, UserExternalDto> profileMap =
        userProfiles.stream().collect(Collectors.toMap(UserExternalDto::getId, u -> u));
    Map<Long, AuthExternalDto> authMap =
        authInfos.stream().collect(Collectors.toMap(AuthExternalDto::getId, a -> a));

    List<UserModerationResponse> content =
        reportedUsersPage.getContent().stream()
            .map(
                idCount -> {
                  Long uid = Long.valueOf(String.valueOf(idCount.getId()));

                  UserExternalDto profile = profileMap.get(uid);
                  AuthExternalDto auth = authMap.get(uid);

                  if (profile == null || auth == null) return null;

                  return new UserModerationResponse(
                      uid,
                      auth.getUsername(), // Từ Auth
                      auth.getEmail(), // Từ Auth
                      profile.getDisplayName(), // Từ User
                      profile.getAvatarUrl(), // Từ User
                      auth.getStatus(), // Từ Auth
                      idCount.getCount());
                })
            .filter(Objects::nonNull)
            .toList();

    return buildPageVO(reportedUsersPage, content);
  }

  @Override
  public PageVO<PostResponse> getFlaggedPosts(String filter, Pageable pageable) {
    Page<IdCount> flagged = reportRepository.findTopReportedTargets(TargetType.POST, pageable);
    if (flagged.isEmpty()) return buildEmptyPageVO(flagged);

    List<Long> postIds =
        flagged.getContent().stream().map(i -> Long.valueOf(String.valueOf(i.getId()))).toList();

    List<PostResponse> posts = mediaClient.getPostsByIds(postIds);

    enrichWithCounts(
        posts,
        PostResponse::getId,
        PostResponse::setReportCount,
        PostResponse::setComplaintCount,
        TargetType.POST);
    return buildPageVO(flagged, posts);
  }

  @Override
  public PostResponse getPostDetailForAdmin(Long postId) {
    List<PostResponse> posts = mediaClient.getPostsByIds(List.of(postId));

    if (posts == null || posts.isEmpty()) {
      throw new ResourceNotFoundException("The post does not exist or has been deleted");
    }

    PostResponse post = posts.get(0);

    enrichWithCounts(
        List.of(post),
        PostResponse::getId,
        PostResponse::setReportCount,
        PostResponse::setComplaintCount,
        TargetType.POST);

    return post;
  }

  @Override
  public PageVO<CommentResponse> getFlaggedComments(String filter, Pageable pageable) {
    Page<IdCount> flagged = reportRepository.findTopReportedTargets(TargetType.COMMENT, pageable);
    if (flagged.isEmpty()) return buildEmptyPageVO(flagged);

    List<Long> commentIds =
        flagged.getContent().stream().map(i -> Long.valueOf(String.valueOf(i.getId()))).toList();

    List<CommentResponse> comments = mediaClient.getCommentsByIds(commentIds);

    enrichWithCounts(
        comments,
        CommentResponse::getId,
        CommentResponse::setReportCount,
        CommentResponse::setComplaintCount,
        TargetType.COMMENT);
    return buildPageVO(flagged, comments);
  }

  @Override
  public CommentResponse getCommentDetailForAdmin(Long commentId) {
    // Tận dụng hàm Batch để lấy 1 bình luận
    List<CommentResponse> comments = mediaClient.getCommentsByIds(List.of(commentId));

    if (comments == null || comments.isEmpty()) {
      throw new ResourceNotFoundException("The comment does not exist or has been deleted");
    }

    CommentResponse comment = comments.get(0);

    enrichWithCounts(
        List.of(comment),
        CommentResponse::getId,
        CommentResponse::setReportCount,
        CommentResponse::setComplaintCount,
        TargetType.COMMENT);

    return comment;
  }

  @Override
  public PageVO<ModerationMessageResponse> getFlaggedMessages(String filter, Pageable pageable) {
    Page<IdCount> flagged = reportRepository.findTopReportedTargets(TargetType.MESSAGE, pageable);
    if (flagged.isEmpty()) return buildEmptyPageVO(flagged);

    List<String> msgIds =
        flagged.getContent().stream().map(i -> String.valueOf(i.getId())).toList();

    List<ModerationMessageResponse> messages = chatClient.getMessagesByIds(msgIds);

    enrichWithCounts(
        messages,
        ModerationMessageResponse::getId,
        ModerationMessageResponse::setReportCount,
        ModerationMessageResponse::setComplaintCount,
        TargetType.MESSAGE);
    return buildPageVO(flagged, messages);
  }

  @Override
  public PageVO<GroupedFlaggedMessageResponse> getGroupedFlaggedMessages(Pageable pageable) {
    // Tạm thời nếu làm API Composition, tính năng Group này nên được viết 1 API bên ChatService
    // (VD: getReportedConversations)
    // Sau đó Moderation gọi sang để lấy, thay vì Moderation tự Group.
    throw new UnsupportedOperationException(
        "Tính năng Group Message cần gọi API tổng hợp từ Chat Service");
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ModerationLogResponse> getModerationLogs(String filter, Pageable pageable) {
    Specification<ModerationLog> spec = null;
    if (filter != null && !filter.isBlank()) {
      spec = RSQLJPASupport.toSpecification(filter);
    }
    if (spec == null) {
      spec = (root, query, cb) -> cb.conjunction();
    }

    Page<ModerationLog> page = moderationLogRepository.findAll(spec, pageable);
    List<ModerationLogResponse> content =
        page.getContent().stream().map(this::mapLogToResponse).toList();
    return buildPageVO(page, content);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ModerationLogResponse> getHistory(
      TargetType type, String id, Pageable pageable, String filter) {
    Page<ModerationLog> page = moderationLogRepository.findHistory(type, id, filter, pageable);
    List<ModerationLogResponse> content =
        page.getContent().stream().map(this::mapLogToResponse).toList();
    return buildPageVO(page, content);
  }

  @Override
  @Transactional
  public void updateUserStatus(Long targetUserId, AccountStatus newStatus, String reason) {
    Long adminId = getCurrentAdminIdSafely();
    if (adminId != null && adminId.equals(targetUserId)) {
      throw new BadRequestException("Bạn không thể tự khóa/mở khóa tài khoản của chính mình.");
    }

    // 🔥 Ra lệnh cho User Service qua Kafka
    UserModerationEvent event = new UserModerationEvent(targetUserId, newStatus.name(), reason);
    kafkaTemplate.send(MODERATION_ACTIONS_TOPIC, targetUserId.toString(), event);

    saveLog(adminId, TargetType.USER, targetUserId.toString(), newStatus.name(), reason);
  }

  @Override
  @Transactional
  public void blockContent(String idStr, TargetType targetType) {
    Long adminId = getCurrentAdminIdSafely();

    ContentModerationEvent event = new ContentModerationEvent(idStr, targetType, "BLOCK");
    kafkaTemplate.send(MODERATION_ACTIONS_TOPIC, idStr, event);

    saveLog(adminId, targetType, idStr, "BLOCK", "Admin blocked content");
  }

  @Override
  @Transactional
  public void unblockContent(Long id, TargetType targetType) {
    Long adminId = getCurrentAdminIdSafely();

    ContentModerationEvent event =
        new ContentModerationEvent(String.valueOf(id), targetType, "UNBLOCK");
    kafkaTemplate.send(MODERATION_ACTIONS_TOPIC, String.valueOf(id), event);

    saveLog(adminId, targetType, String.valueOf(id), "UNBLOCK", "Admin restored content");
  }

  // --- CÁC HÀM AUTO MODERATION (BOT) ---
  @Override
  public void validatePostContent(PostExternalDto post) {
    // Logic AI gọi ở đây (AI check toxic)
    // Nếu toxic -> createSystemAutoReport(post.getId(), TargetType.POST, "AI Reason");
  }

  @Override
  public void validateImage(Long mediaId, byte[] imageBytes, String filename) {
    // Logic AI check ảnh
  }

  @Override
  public void validateTextContent(Long targetId, TargetType targetType, String content) {
    //    ModerationResult result = aiServiceClient.checkContentToxicity(content);
    ModerationResult result = null;

    if (result.isToxic()) {
      createSystemAutoReport(targetId, targetType, result.getReason());
    }
  }

  @Override
  public void validateMediaContent(Long targetId, TargetType targetType, String mediaUrl) {
    //    ModerationResult result = aiServiceClient.checkImageToxicityUrl(mediaUrl);
    ModerationResult result = null;

    if (result.isToxic()) {
      createSystemAutoReport(targetId, targetType, "[CẢNH BÁO ẢNH] " + result.getReason());
    }
  }

  private void createSystemAutoReport(Long targetId, TargetType targetType, String aiReason) {
    try {
      boolean exists =
          reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
              String.valueOf(targetId), targetType);
      if (exists) return; // Nếu đã bị ban rồi thì thôi không report nữa

      Report report =
          Report.builder()
              .reporterId(-1L) // Gán ID -1 (hoặc null) để đánh dấu là Bot
              .targetId(String.valueOf(targetId))
              .targetType(targetType)
              .targetUserId(0L)
              .reason(ReportReason.HARASSMENT)
              .customReason("AI tự động phát hiện vi phạm: " + aiReason)
              .status(ReportStatus.APPROVED)
              .isBannedBySystem(true)
              .createdAt(Instant.now())
              .build();

      reportRepository.save(report);
      log.warn("🚨 [SYSTEM BAN] Đã tự động report và phạt nội dung {}: {}", targetType, targetId);

      // 🔥 QUAN TRỌNG: Gọi ngược hàm blockContent để bắn Kafka ra lệnh xóa bài ngay lập tức!
      this.blockContent(targetId.toString(), targetType);

    } catch (Exception e) {
      log.error("Lỗi khi tạo System Report: {}", e.getMessage());
    }
  }

  // --- HELPER METHODS ---

  private void saveLog(
      Long actorId, TargetType type, String targetId, String action, String reason) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(actorId)
            .targetType(type)
            .targetId(targetId)
            .action(action)
            .reason(reason)
            .createdAt(Instant.now())
            .build());
  }

  private ModerationLogResponse mapLogToResponse(ModerationLog log) {
    return ModerationLogResponse.builder()
        .id(log.getId())
        .targetType(log.getTargetType())
        .targetId(log.getTargetId())
        .action(log.getAction())
        .reason(log.getReason())
        .createdAt(log.getCreatedAt())
        .actorId(log.getActorId())
        .actorName(log.getActorId() != null ? "Admin ID: " + log.getActorId() : "System (AI)")
        .build();
  }

  private <T> void enrichWithCounts(
      List<T> responses,
      Function<T, Object> idExtractor,
      BiConsumer<T, Long> setReport,
      BiConsumer<T, Long> setComplaint,
      TargetType type) {
    if (responses.isEmpty()) return;
    List<String> ids = responses.stream().map(idExtractor).map(String::valueOf).toList();

    Map<String, Long> reportCounts = new HashMap<>();
    try {
      reportRepository
          .countByTargetTypeAndTargetIdIn(type, ids)
          .forEach(item -> reportCounts.put(String.valueOf(item.getId()), item.getCount()));
    } catch (Exception ignored) {
    }

    Map<String, Long> complaintCounts = new HashMap<>();
    try {
      complaintRepository
          .countByTargetTypeAndTargetIdIn(type, ids)
          .forEach(item -> complaintCounts.put(String.valueOf(item.getId()), item.getCount()));
    } catch (Exception ignored) {
    }

    for (T res : responses) {
      String idStr = String.valueOf(idExtractor.apply(res));
      setReport.accept(res, reportCounts.getOrDefault(idStr, 0L));
      setComplaint.accept(res, complaintCounts.getOrDefault(idStr, 0L));
    }
  }

  private <T> PageVO<T> buildPageVO(Page<?> page, List<T> content) {
    return PageVO.<T>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  private <T> PageVO<T> buildEmptyPageVO(Page<?> page) {
    return PageVO.<T>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(0)
        .content(Collections.emptyList())
        .build();
  }
}
