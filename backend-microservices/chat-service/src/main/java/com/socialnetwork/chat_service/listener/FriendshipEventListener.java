package com.socialnetwork.chat_service.listener;

import com.socialnetwork.chat_service.service.ConversationService;
import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.FriendAcceptedEvent;
import com.socialnetwork.common.events.FriendshipDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the private rooms in sync with the friend graph. Exceptions are left to propagate so the
 * shared error handler can retry and, ultimately, dead-letter the record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.FRIENDSHIP_EVENTS)
public class FriendshipEventListener {

  private final ConversationService conversationService;

  @KafkaHandler
  public void onFriendAccepted(FriendAcceptedEvent event) {
    log.info("Friendship accepted between users {} and {}", event.senderId(), event.receiverId());
    conversationService.createConversationForFriends(event.senderId(), event.receiverId());
  }

  @KafkaHandler
  public void onFriendshipDeleted(FriendshipDeletedEvent event) {
    log.info("Friendship removed between users {} and {}", event.user1Id(), event.user2Id());
    conversationService.archiveConversationForFriends(event.user1Id(), event.user2Id());
  }

  @KafkaHandler(isDefault = true)
  public void onUnknown(Object payload) {
    log.warn(
        "Ignoring unsupported payload of type {} on topic {}",
        payload == null ? "null" : payload.getClass().getName(),
        KafkaTopics.FRIENDSHIP_EVENTS);
  }
}
