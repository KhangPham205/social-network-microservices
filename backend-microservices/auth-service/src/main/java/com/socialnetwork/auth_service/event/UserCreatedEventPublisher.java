package com.socialnetwork.auth_service.event;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts the registration saga. When called inside a transaction the event is published only after
 * the commit, so consumers never see an account that was rolled back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void publishAfterCommit(UserCreatedEvent event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              send(event);
            }
          });
    } else {
      send(event);
    }
  }

  private void send(UserCreatedEvent event) {
    kafkaTemplate
        .send(KafkaTopics.USER_CREATED, String.valueOf(event.accountId()), event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error(
                    "Failed to publish UserCreatedEvent for accountId {}", event.accountId(), ex);
              } else {
                log.info("Published UserCreatedEvent for accountId {}", event.accountId());
              }
            });
  }
}
