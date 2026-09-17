package com.socialnetwork.media_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheSyncHandler {

  private final UserCacheRepository userCacheRepository;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "user-created-topic", groupId = "media-service-group-v1")
  @Transactional
  public void handleUserCreatedEvent(ConsumerRecord<String, Object> record) {
    try {
      Object payload = record.value();
      UserCreatedEvent event = objectMapper.convertValue(payload, UserCreatedEvent.class);
      log.info("Received UserCreatedEvent to sync UserCache for accountId: {}", event.accountId());

      UserCache userCache =
          UserCache.builder()
              .id(event.accountId())
              .displayName(event.username())
              .avatarUrl(null)
              .build();

      userCacheRepository.save(userCache);
      log.info("Successfully synced UserCache for accountId: {}", event.accountId());

    } catch (Exception e) {
      log.error("Failed to process UserCreatedEvent for UserCache sync", e);
    }
  }

  // (Mở rộng sau này) Lắng nghe sự kiện CẬP NHẬT profile từ user-service
  /*
  @KafkaListener(topics = "profile-updated-topic", groupId = "media-service-group-v1")
  @Transactional
  public void handleProfileUpdatedEvent(Object messagePayload) {
      try {
          ProfileUpdatedEvent event = objectMapper.convertValue(messagePayload, ProfileUpdatedEvent.class);

          userCacheRepository.findById(event.accountId()).ifPresent(cache -> {
              cache.setDisplayName(event.displayName());
              cache.setAvatarUrl(event.avatarUrl());
              userCacheRepository.save(cache);
              log.info("Updated UserCache for accountId: {}", event.accountId());
          });
      } catch (Exception e) {
          log.error("Failed to sync Profile Update", e);
      }
  }
  */
}
