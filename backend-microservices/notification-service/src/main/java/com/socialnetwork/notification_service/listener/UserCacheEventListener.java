package com.socialnetwork.notification_service.listener;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.notification_service.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Keeps {@code user_caches} in sync with auth-service and user-service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCacheEventListener {

  private final UserCacheService userCacheService;

  @KafkaListener(topics = KafkaTopics.USER_CREATED)
  public void onUserCreated(UserCreatedEvent event) {
    log.debug("Received UserCreatedEvent for account {}", event.accountId());
    userCacheService.onUserCreated(event);
  }

  @KafkaListener(topics = KafkaTopics.PROFILE_UPDATED)
  public void onProfileUpdated(ProfileUpdatedEvent event) {
    log.debug("Received ProfileUpdatedEvent for account {}", event.accountId());
    userCacheService.onProfileUpdated(event);
  }
}
