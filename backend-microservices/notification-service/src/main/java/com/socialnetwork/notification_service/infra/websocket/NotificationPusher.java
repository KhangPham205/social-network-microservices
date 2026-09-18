package com.socialnetwork.notification_service.infra.websocket;

import com.socialnetwork.common.constants.WebSocketConstants;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Outbound STOMP adapter. When called inside a transaction the push is deferred until the
 * transaction has committed, so clients never see notifications that were rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPusher {

  private final SimpMessagingTemplate messagingTemplate;

  public void pushNotification(Long receiverId, NotificationDto notification) {
    afterCommit(
        () ->
            messagingTemplate.convertAndSendToUser(
                receiverId.toString(), WebSocketConstants.NOTIFICATIONS_QUEUE, notification));
  }

  public void pushUnreadCount(Long receiverId, long unreadCount) {
    afterCommit(
        () ->
            messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                WebSocketConstants.NOTIFICATION_SUMMARY_QUEUE,
                new NotificationCountDto(unreadCount)));
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
