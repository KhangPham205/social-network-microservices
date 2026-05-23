package com.socialnetwork.media_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.events.ContentModerationEvent;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationActionEventListener {

  private final PostService postService;
  private final CommentService commentService;
  private final ObjectMapper objectMapper; // 🔥 Dùng để tự parse JSON

  @KafkaListener(topics = "moderation-actions", groupId = "media-service-group-v2")
  public void handleModerationAction(String messagePayload) {
    try {
      ContentModerationEvent event = objectMapper.readValue(messagePayload, ContentModerationEvent.class);

      log.info("Nhận được lệnh từ Moderation: Action={}, Type={}, ID={}",
          event.getAction(), event.getTargetType(), event.getTargetId());

      Long targetId = Long.parseLong(event.getTargetId());
      boolean isBanned = "BLOCK".equalsIgnoreCase(event.getAction());

      if ("POST".equalsIgnoreCase(event.getTargetType())) {
        postService.updateSystemBanStatus(targetId, isBanned);

      } else if ("COMMENT".equalsIgnoreCase(event.getTargetType())) {
        commentService.updateSystemBanStatus(targetId, isBanned);

      } else {
        log.warn("TargetType không được hỗ trợ bởi Media Service: {}", event.getTargetType());
      }

    } catch (Exception e) {
      log.error("Lỗi khi xử lý tín hiệu Kafka: Message [{}]. Chi tiết: {}", messagePayload, e.getMessage());
    }
  }
}