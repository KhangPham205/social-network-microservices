package com.socialnetwork.media_service.listener;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ContentCreatedEvent;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Second consumer of {@code CONTENT_CREATED}: indexes new posts for the recommendation feed. It
 * runs in its own group so indexing never competes with moderation for the same records.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentIndexingListener {

  static final String GROUP_ID = "media-service-recommendation";

  private final RecommendationService recommendationService;

  @KafkaListener(topics = KafkaTopics.CONTENT_CREATED, groupId = GROUP_ID)
  public void onContentCreated(ContentCreatedEvent event) {
    if (event.targetType() != TargetType.POST) {
      log.debug("Skipping {} event, only posts are indexed", event.targetType());
      return;
    }
    if (event.targetId() == null) {
      throw new IllegalArgumentException("ContentCreatedEvent.targetId is required");
    }
    recommendationService.indexPost(event.targetId());
  }
}
