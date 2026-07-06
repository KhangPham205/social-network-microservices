package com.socialnetwork.chat_service.listener;

import com.socialnetwork.chat_service.service.ConversationService;
import events.FriendAcceptedEvent;
import events.FriendshipDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(topics = "friendship-events")
public class FriendshipEventListener {

  private final ConversationService conversationService;

  @KafkaHandler
  public void handleFriendshipAccepted(FriendAcceptedEvent event) {
    try {
      log.info(
          "Received FriendAcceptedEvent: senderId={}, receiverId={}",
          event.senderId(),
          event.receiverId());
      conversationService.createConversationForFriends(event.senderId(), event.receiverId());
      log.info(
          "Successfully created conversation for users {} and {}",
          event.senderId(),
          event.receiverId());
    } catch (Exception e) {
      log.error("Error creating conversation: {}", e.getMessage(), e);
    }
  }

  @KafkaHandler
  public void handleFriendshipDeleted(FriendshipDeletedEvent event) {
    log.info(
        "Received FriendshipDeletedEvent: user1Id={}, user2Id={}",
        event.user1Id(),
        event.user2Id());
  }

  @KafkaHandler(isDefault = true)
  public void unknown(Object object) {
    log.warn("Received unknown message type on friendship-events topic: {}", object);
  }
}
