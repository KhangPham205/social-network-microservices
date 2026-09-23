package com.socialnetwork.media_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes domain events only once the writing transaction has committed, so consumers never see
 * an event for a row that was rolled back. Records are sent as objects (the configured
 * {@code JacksonJsonSerializer} does the encoding) and keyed by the aggregate id so all events of
 * one post or comment keep their order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void publishAfterCommit(String topic, String key, Object event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              send(topic, key, event);
            }
          });
      return;
    }
    send(topic, key, event);
  }

  private void send(String topic, String key, Object event) {
    kafkaTemplate
        .send(topic, key, event)
        .whenComplete(
            (result, error) -> {
              if (error != null) {
                log.error("Failed to publish {} to topic {} (key {})",
                    event.getClass().getSimpleName(), topic, key, error);
              } else {
                log.debug("Published {} to topic {} (key {})",
                    event.getClass().getSimpleName(), topic, key);
              }
            });
  }
}
