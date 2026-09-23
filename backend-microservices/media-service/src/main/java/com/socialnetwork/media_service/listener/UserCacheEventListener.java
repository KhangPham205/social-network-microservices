package com.socialnetwork.media_service.listener;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.media_service.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds {@code user_caches}, the read model every post and comment author is rendered from.
 * Exceptions are left to bubble up so the record is retried and eventually parked in the DLT.
 */
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
