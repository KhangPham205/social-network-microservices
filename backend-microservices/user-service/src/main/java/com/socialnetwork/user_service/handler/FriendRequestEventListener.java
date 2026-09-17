package com.socialnetwork.user_service.handler;

import events.FriendRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendRequestEventListener {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @EventListener
  public void handleFriendRequestEvent(FriendRequestEvent event) {
    try {
      log.info(
          "📤 Publishing FriendRequestEvent to Kafka: sender={}, receiver={}",
          event.senderId(),
          event.receiverId());

      // Publish to friend-request-topic để notification-service xử lý
      kafkaTemplate.send("friend-request-topic", String.valueOf(event.receiverId()), event);

      log.info("✅ FriendRequestEvent published successfully to friend-request-topic");
    } catch (Exception e) {
      log.error(
          "❌ Failed to publish FriendRequestEvent: sender={}, receiver={}, error={}",
          event.senderId(),
          event.receiverId(),
          e.getMessage());
    }
  }
}
