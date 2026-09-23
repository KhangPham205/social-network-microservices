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

/**
 * Applies BLOCK / UNBLOCK commands from moderation-service to posts and comments. Malformed events
 * raise {@link IllegalArgumentException}, which the shared error handler treats as non-retryable
 * and routes straight to the dead-letter topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationActionEventListener {

  private final PostService postService;
  private final CommentService commentService;

  @KafkaListener(topics = KafkaTopics.MODERATION_ACTIONS)
  public void onModerationAction(ModerationActionEvent event) {
    if (event.targetType() == null || event.action() == null || event.targetId() == null) {
      throw new IllegalArgumentException(
          "ModerationActionEvent requires targetId, targetType and action");
    }
    log.info(
        "Moderation command received: action={}, type={}, id={}",
        event.action(),
        event.targetType(),
        event.targetId());

    boolean banned = event.action() == ModerationAction.BLOCK;
    switch (event.targetType()) {
      case POST -> postService.updateSystemBanStatus(parseId(event.targetId()), banned);
      case COMMENT -> commentService.updateSystemBanStatus(parseId(event.targetId()), banned);
      default -> log.debug("Target type {} is not handled by media-service", event.targetType());
    }
  }

  private static Long parseId(String targetId) {
    try {
      return Long.valueOf(targetId);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Target id is not numeric: " + targetId, e);
    }
  }
}
