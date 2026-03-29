package com.socialnetwork.media_service.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheSyncHandler {

  private final UserCacheRepository userCacheRepository;
  private final ObjectMapper objectMapper;

  // 1. Lắng nghe sự kiện TẠO MỚI user từ auth-service
  @KafkaListener(topics = "user-created-topic", groupId = "media-service-group-v1")
  @Transactional
  public void handleUserCreatedEvent(Object messagePayload) {
    try {
      // Ép kiểu từ LinkedHashMap sang UserCreatedEvent
      UserCreatedEvent event = objectMapper.convertValue(messagePayload, UserCreatedEvent.class);
      log.info("Received UserCreatedEvent to sync UserCache for accountId: {}", event.accountId());

      // Lưu vào DB nội bộ của Media Service
      UserCache userCache =
          UserCache.builder()
              .id(event.accountId())
              .displayName(event.username()) // Lấy username làm tên hiển thị tạm thời
              .avatarUrl(null) // Mới tạo chưa có avatar
              .build();

      userCacheRepository.save(userCache);
      log.info("Successfully synced UserCache for accountId: {}", event.accountId());

    } catch (Exception e) {
      log.error("Failed to process UserCreatedEvent for UserCache sync", e);
    }
  }

  // 2. (Mở rộng sau này) Lắng nghe sự kiện CẬP NHẬT profile từ user-service
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
