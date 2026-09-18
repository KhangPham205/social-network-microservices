package com.socialnetwork.notification_service.listener;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.MessageNotificationEvent;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter: validation and business rules live in {@link NotificationService}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationService notificationService;

  @KafkaListener(topics = KafkaTopics.NOTIFICATION)
  public void onNotificationEvent(NotificationEvent event) {
    log.debug("Received NotificationEvent {}", event.eventId());
    notificationService.createNotification(event);
  }

  @KafkaListener(topics = KafkaTopics.CHAT_NOTIFICATION)
  public void onMessageNotificationEvent(MessageNotificationEvent event) {
    log.debug("Received MessageNotificationEvent for message {}", event.messageId());
    notificationService.createMessageNotifications(event);
  }
}
