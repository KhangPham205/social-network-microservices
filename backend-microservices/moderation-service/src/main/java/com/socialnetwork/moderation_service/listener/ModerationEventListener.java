package com.socialnetwork.moderation_service.listener;

import com.socialnetwork.moderation_service.event.ContentCreatedEvent;
import com.socialnetwork.moderation_service.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = "post-events", groupId = "moderation-service-group-v1")
public class ModerationEventListener {

  private final ModerationService moderationService;

  @KafkaHandler
  public void handleContentCreated(ContentCreatedEvent event) {
    log.info(
        "🤖 Nhận được tín hiệu có bài viết mới. Tiến hành quét: Type={}, ID={}",
        event.getTargetType(),
        event.getTargetId());

    try {
      if (event.getContent() != null && !event.getContent().isBlank()) {
        moderationService.validateTextContent(
            event.getTargetId(), event.getTargetType(), event.getContent());
      }

      // 2. Quét ảnh/video (Image/Video moderation)
      if (event.getMedia() != null && !event.getMedia().isEmpty()) {
        for (var mediaItem : event.getMedia()) {
          String url = mediaItem.get("url");
          moderationService.validateMediaContent(event.getTargetId(), event.getTargetType(), url);
        }
      }

      log.info("✅ Quét thành công ID: {}", event.getTargetId());

    } catch (Exception e) {
      log.error(
          "❌ Lỗi trong quá trình AI quét nội dung ID {}: {}", event.getTargetId(), e.getMessage());
    }
  }

  @KafkaHandler(isDefault = true)
  public void handleUnknown(Object object) {
    log.warn("Nhận được Event lạ không xác định trên topic post-events: {}", object);
  }
}
