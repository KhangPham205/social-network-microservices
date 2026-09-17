package com.socialnetwork.notification_service.handler;

import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.UserCacheRepository;
import events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventHandler {

  private final UserCacheRepository userCacheRepository;

  @KafkaListener(topics = "user-created-topic", groupId = "notification-service-group")
  @Transactional
  public void handleUserCreatedEvent(UserCreatedEvent event) {
    log.info("UserCreatedEvent received for user: {}", event.accountId());

    // Cache user with basic info (displayName and avatarUrl will be updated later from profile
    // service)
    UserCache userCache =
        UserCache.builder()
            .id(event.accountId())
            .displayName(event.username()) // Use username as default displayName
            .avatarUrl(null) // Will be updated when user completes profile
            .build();

    userCacheRepository.save(userCache);
    log.info("✅ User cached: {} - {}", event.accountId(), event.username());
  }
}
