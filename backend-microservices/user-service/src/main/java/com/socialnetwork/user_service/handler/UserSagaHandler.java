package com.socialnetwork.user_service.handler;

import com.socialnetwork.user_service.service.UserService;
import events.ProfileCreatedEvent;
import events.ProfileFailedEvent;
import events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSagaHandler {

  private final UserService userService;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  // ĐỔI SANG GROUP v3 ĐỂ ÉP ĐỌC LẠI TỪ ĐẦU
  @KafkaListener(topics = "user-created-topic", groupId = "user-service-group")
  public void handleUserCreatedEvent(UserCreatedEvent event) {
    log.info("Received UserCreatedEvent for accountId: {}", event.accountId());

    try {
      userService.createDefaultProfile(event.accountId(), event.username());
      kafkaTemplate.send("profile-created-topic", new ProfileCreatedEvent(event.accountId()));
    } catch (Exception e) {
      log.error("Failed to create profile for accountId: {}", event.accountId(), e);
      kafkaTemplate.send(
          "profile-failed-topic", new ProfileFailedEvent(event.accountId(), e.getMessage()));
    }
  }
}
