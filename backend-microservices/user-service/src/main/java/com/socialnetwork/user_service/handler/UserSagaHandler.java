package com.socialnetwork.user_service.handler;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.user_service.event.EventPublisher;
import com.socialnetwork.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Registration saga, user-service side: create the profile for a freshly created account and answer
 * auth-service. Redeliveries are expected, so {@code createDefaultProfile} is idempotent and a
 * profile that already exists simply gets its reply re-published.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserSagaHandler {

  private final UserService userService;
  private final EventPublisher eventPublisher;

  @KafkaListener(topics = KafkaTopics.USER_CREATED)
  public void handleUserCreatedEvent(UserCreatedEvent event) {
    log.info("Received UserCreatedEvent for accountId {}", event.accountId());
    try {
      UserSummary summary = userService.createDefaultProfile(event.accountId(), event.username());
      eventPublisher.publishProfileCreated(summary.id());
    } catch (Exception e) {
      // The saga owns the failure path: auth-service compensates on ProfileFailedEvent.
      log.error("Failed to create the profile for accountId {}", event.accountId(), e);
      eventPublisher.publishProfileFailed(event.accountId(), e.getMessage());
    }
  }
}
