package com.socialnetwork.moderation_service.listener;

import com.socialnetwork.moderation_service.enums.ReportReason;
import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.event.ContentCreatedEvent;
import com.socialnetwork.moderation_service.event.MessageSentEvent;
import com.socialnetwork.moderation_service.model.ModerationLog;
import com.socialnetwork.moderation_service.model.Report;
import com.socialnetwork.moderation_service.repository.ModerationLogRepository;
import com.socialnetwork.moderation_service.repository.ReportRepository;
import com.socialnetwork.moderation_service.service.ModerationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vo.TargetType;

/**
 * ContentModerationListener - Listens for content creation events from Kafka and performs AI-based
 * moderation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentModerationListener {

  private final ModerationLogRepository moderationLogRepository;
  private final ReportRepository reportRepository;
  private final ModerationService moderationService;

  /** Handle content creation events via Kafka */
  @Async
  @KafkaListener(topics = "content-created", groupId = "moderation-service-group")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleContentCreationViaKafka(ContentCreatedEvent event) {
    log.info(
        "🤖 Moderation: Processing content [{}] ID: {}",
        event.getTargetType(),
        event.getTargetId());

    try {
      // A. Check text content for toxicity
      String textToCheck = event.getContent();
      if (textToCheck != null && !textToCheck.isBlank()) {
        boolean isToxic = performTextModeration(textToCheck);
        if (isToxic) {
          log.warn("❌ Toxic content detected");
          handleToxicContent(event);
          return;
        }
      }

      // B. Check media (images, etc.)
      if (event.getMedia() != null && !event.getMedia().isEmpty()) {
        for (Map<String, String> mediaItem : event.getMedia()) {
          String url = mediaItem.get("url");
          if (isImage(url)) {
            boolean imageIsToxic = performImageModeration(url);
            if (imageIsToxic) {
              log.warn("❌ Toxic image detected: {}", url);
              handleToxicContent(event);
              return;
            }
          }
        }
      }

      log.info("✅ Content [{} - {}] passed moderation", event.getTargetType(), event.getTargetId());

    } catch (Exception e) {
      log.error("❌ Error during content moderation: {}", e.getMessage(), e);
    }
  }

  /** Handle message sent events via Kafka */
  @Async
  @KafkaListener(topics = "message-created", groupId = "moderation-service-group")
  public void handleMessageSentEvent(MessageSentEvent event) {
    log.info("🤖 Moderation: Processing message ID: {}", event.getId());
    boolean isToxic = false;
    String reason = null;

    try {
      // Check text content
      if (event.getContent() != null && !event.getContent().isBlank()) {
        if (performTextModeration(event.getContent())) {
          isToxic = true;
          reason = "Toxic message content detected";
        }
      }

      // Check media if text is clean
      if (!isToxic && event.getMedia() != null && !event.getMedia().isEmpty()) {
        for (Map<String, Object> mediaItem : event.getMedia()) {
          String url = (String) mediaItem.get("url");
          if (isImage(url) && performImageModeration(url)) {
            isToxic = true;
            reason = "Toxic image in message detected";
            break;
          }
        }
      }

      // Handle result
      if (isToxic) {
        log.warn("❌ Banning message {}: {}", event.getId(), reason);
        moderationService.blockContent(event.getId(), TargetType.MESSAGE);
        saveModerationLog(TargetType.MESSAGE, event.getId(), reason);
      } else {
        log.info("✅ Message {} is clean", event.getId());
      }

    } catch (Exception e) {
      log.error("⚠️ Error processing message {}: {}", event.getId(), e.getMessage());
    }
  }

  /** Placeholder for text moderation - replace with real AI service call */
  private boolean performTextModeration(String content) {
    // TODO: Integrate with external AI service
    if (content == null || content.isBlank()) {
      return false;
    }
    // Simple pattern check (replace with real AI service)
    String lowerContent = content.toLowerCase();
    List<String> badWords = List.of("spam", "abuse", "hate");
    return badWords.stream().anyMatch(lowerContent::contains);
  }

  /** Placeholder for image moderation */
  private boolean performImageModeration(String url) {
    // TODO: Integrate with AI image analysis service
    return false;
  }

  /** Handle toxic content - create report and log moderation */
  private void handleToxicContent(ContentCreatedEvent event) {
    try {
      createSystemReportForContent(
          event.getTargetId().toString(),
          event.getTargetType(),
          event.getAuthorId(),
          "Auto-detected toxic content");
      saveModerationLog(
          event.getTargetType(),
          event.getTargetId().toString(),
          "Auto-banned due to toxic content");
      moderationService.blockContent(event.getTargetId().toString(), event.getTargetType());
      log.info("🚫 Auto-banned {} ID: {}", event.getTargetType(), event.getTargetId());
    } catch (Exception e) {
      log.error("Error handling toxic content: {}", e.getMessage());
    }
  }

  /** Create a system-generated report */
  private void createSystemReportForContent(
      String targetId, TargetType type, Long targetUserId, String reason) {
    try {
      boolean exists =
          reportRepository.existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(
              targetId, type);
      if (!exists) {
        Report report =
            Report.builder()
                .reporterId(null)
                .targetId(targetId)
                .targetType(type)
                .targetUserId(targetUserId)
                .reason(ReportReason.HARASSMENT)
                .customReason(reason)
                .isBannedBySystem(true)
                .status(ReportStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        reportRepository.save(report);
      }
    } catch (Exception e) {
      log.error("⚠️ Failed to create system report: {}", e.getMessage());
    }
  }

  /** Log moderation action */
  private void saveModerationLog(TargetType type, String targetId, String reason) {
    try {
      ModerationLog logEntry =
          ModerationLog.builder()
              .targetType(type)
              .targetId(targetId)
              .action("AUTO_BAN")
              .reason(reason)
              .actorId(null)
              .createdAt(Instant.now())
              .build();
      moderationLogRepository.save(logEntry);
    } catch (Exception e) {
      log.error("Error saving moderation log: {}", e.getMessage());
    }
  }

  private boolean isImage(String url) {
    if (url == null) return false;
    String lower = url.toLowerCase();
    return lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".webp")
        || lower.endsWith(".bmp");
  }
}
