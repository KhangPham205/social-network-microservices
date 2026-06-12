package com.socialnetwork.moderation_service.listener;

import com.socialnetwork.moderation_service.client.AiServiceClient;
import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
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
import tools.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper;
  private final AiServiceClient aiServiceClient;

  /** Handle content creation events via Kafka */
  @Async
  @KafkaListener(topics = "content-created-topic", groupId = "moderation-service-group")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handleContentCreationViaKafka(String payload) {
    try {
      ContentCreatedEvent event = objectMapper.readValue(payload, ContentCreatedEvent.class);
      log.info(
          "🤖 Moderation: Processing content [{}] ID: {}",
          event.getTargetType(),
          event.getTargetId());

      AiModerationRequest aiRequest =
          AiModerationRequest.builder().text(event.getContent()).media(event.getMedia()).build();

      AiModerationResponse aiResponse = aiServiceClient.checkToxicity(aiRequest);
      log.info(
          "AI evaluation for {} {}: isToxic={}, reason={}",
          event.getTargetType(),
          event.getTargetId(),
          aiResponse.isToxic(),
          aiResponse.getReason());

      if (aiResponse.isToxic()) {
        log.warn("❌ Toxic content detected: {}", aiResponse.getReason());
        handleToxicContent(event, aiResponse.getReason());
      } else {
        log.info(
            "✅ Content [{} - {}] passed moderation", event.getTargetType(), event.getTargetId());
      }

    } catch (Exception e) {
      log.error("❌ Error during content moderation: {}", e.getMessage(), e);
    }
  }

  /** Handle message sent events via Kafka */
  @Async
  @KafkaListener(topics = "message-created", groupId = "moderation-service-group")
  public void handleMessageSentEvent(MessageSentEvent event) {
    log.info("🤖 Moderation: Processing message ID: {}", event.getId());

    try {
      // Map MessageSentEvent.media (List<Map<String, Object>>) to List<Map<String, String>>
      List<Map<String, String>> mediaList = null;
      if (event.getMedia() != null) {
        mediaList =
            event.getMedia().stream()
                .map(
                    m -> {
                      Map<String, String> map = new java.util.HashMap<>();
                      for (Map.Entry<String, Object> entry : m.entrySet()) {
                        map.put(
                            entry.getKey(),
                            entry.getValue() != null ? entry.getValue().toString() : "");
                      }
                      return map;
                    })
                .collect(java.util.stream.Collectors.toList());
      }

      AiModerationRequest aiRequest =
          AiModerationRequest.builder().text(event.getContent()).media(mediaList).build();

      AiModerationResponse aiResponse = aiServiceClient.checkToxicity(aiRequest);

      if (aiResponse.isToxic()) {
        log.warn("❌ Banning message {}: {}", event.getId(), aiResponse.getReason());
        moderationService.blockContent(event.getId(), TargetType.MESSAGE);
        saveModerationLog(TargetType.MESSAGE, event.getId(), aiResponse.getReason());
      } else {
        log.info("✅ Message {} is clean", event.getId());
      }

    } catch (Exception e) {
      log.error("⚠️ Error processing message {}: {}", event.getId(), e.getMessage());
    }
  }

  /** Handle toxic content - create report and log moderation */
  private void handleToxicContent(ContentCreatedEvent event, String reason) {
    try {
      createSystemReportForContent(
          event.getTargetId().toString(),
          event.getTargetType(),
          event.getAuthorId(),
          "Auto-detected toxic content: " + reason);
      saveModerationLog(
          event.getTargetType(),
          event.getTargetId().toString(),
          "Auto-banned due to toxic content: " + reason);
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
                .reporterId(-1L) // -1L represents the System/Bot
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
}
