package com.socialnetwork.media_service.listener;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ModerationActionEvent;
import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Applies BLOCK / UNBLOCK commands from moderation-service to posts and comments. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationActionEventListener {

  private final PostService postService;
  private final CommentService commentService;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = KafkaTopics.MODERATION_ACTIONS, groupId = "media-service-group-v2")
  public void handleModerationAction(String payload) {
    try {
      ModerationActionEvent event = objectMapper.readValue(payload, ModerationActionEvent.class);
      log.info(
          "Moderation command received: action={}, type={}, id={}",
          event.action(),
          event.targetType(),
          event.targetId());

      if (event.targetType() == null || event.action() == null) {
        log.warn("Ignoring moderation command with missing type/action: {}", payload);
        return;
      }
      boolean banned = event.action() == ModerationAction.BLOCK;
      switch (event.targetType()) {
        case POST -> postService.updateSystemBanStatus(Long.parseLong(event.targetId()), banned);
        case COMMENT ->
            commentService.updateSystemBanStatus(Long.parseLong(event.targetId()), banned);
        default -> log.debug("TargetType {} is not handled by media-service", event.targetType());
      }
    } catch (Exception e) {
      log.error("Failed to process moderation command: {}", payload, e);
    }
  }
}
