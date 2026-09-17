package com.socialnetwork.media_service.listener;

import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import events.ModerationActionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationActionEventListener {

  private final PostService postService;
  private final CommentService commentService;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "moderation-actions", groupId = "media-service-group-v2")
  public void handleModerationAction(String payload) {
    try {
      ModerationActionEvent event = objectMapper.readValue(payload, ModerationActionEvent.class);

      log.info(
          "Nhận được lệnh từ Moderation: Action={}, Type={}, ID={}",
          event.getAction(),
          event.getTargetType(),
          event.getTargetId());

      Long targetId = Long.parseLong(event.getTargetId());
      boolean isBanned = "BLOCK".equalsIgnoreCase(event.getAction());

      // Ép kiểu về chuỗi so sánh cho an toàn nếu bên gửi dùng Enum
      String typeStr = event.getTargetType().toString().toUpperCase();

      if ("POST".equals(typeStr)) {
        postService.updateSystemBanStatus(targetId, isBanned);
      } else if ("COMMENT".equals(typeStr)) {
        commentService.updateSystemBanStatus(targetId, isBanned);
      } else {
        log.warn("TargetType không được hỗ trợ bởi Media Service: {}", typeStr);
      }

    } catch (Exception e) {
      log.error("Lỗi khi xử lý lệnh Moderation từ Kafka. Chi tiết: {}", e.getMessage(), e);
    }
  }
}
