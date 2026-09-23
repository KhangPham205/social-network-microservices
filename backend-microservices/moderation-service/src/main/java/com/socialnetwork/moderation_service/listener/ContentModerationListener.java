package com.socialnetwork.moderation_service.listener;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.events.MessageCreatedEvent;
import com.socialnetwork.moderation_service.service.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds new content into the AI moderation pipeline. Nothing is caught here on purpose: when the
 * model is unreachable the record is retried and finally dead-lettered, instead of the content
 * silently passing as clean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentModerationListener {

  private final ModerationService moderationService;

  @KafkaListener(topics = KafkaTopics.CONTENT_CREATED)
  public void onContentCreated(ContentCreatedEvent event) {
    log.info("Moderating {} {}", event.targetType(), event.targetId());
    moderationService.moderateContent(event);
  }

  @KafkaListener(topics = KafkaTopics.MESSAGE_CREATED)
  public void onMessageCreated(MessageCreatedEvent event) {
    log.info("Moderating message {}", event.messageId());
    moderationService.moderateMessage(event);
  }
}
