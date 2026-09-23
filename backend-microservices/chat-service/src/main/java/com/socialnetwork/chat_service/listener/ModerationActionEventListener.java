package com.socialnetwork.chat_service.listener;

import com.socialnetwork.chat_service.service.MessageService;
import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ModerationActionEvent;
import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.common.vo.TargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Applies BLOCK / UNBLOCK commands from moderation-service to chat messages. Malformed events raise
 * {@link IllegalArgumentException}, which the shared error handler treats as non-retryable and
 * routes straight to the dead-letter topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationActionEventListener {

  private final MessageService messageService;

  @KafkaListener(topics = KafkaTopics.MODERATION_ACTIONS)
  public void onModerationAction(ModerationActionEvent event) {
    if (event.targetType() == null || event.action() == null || event.targetId() == null) {
      throw new IllegalArgumentException(
          "ModerationActionEvent requires targetId, targetType and action");
    }
    if (event.targetType() != TargetType.MESSAGE) {
      log.debug("Target type {} is not handled by chat-service", event.targetType());
      return;
    }

    log.info(
        "Moderation command received: action={}, messageId={}", event.action(), event.targetId());
    messageService.applySystemBan(event.targetId(), event.action() == ModerationAction.BLOCK);
  }
}
