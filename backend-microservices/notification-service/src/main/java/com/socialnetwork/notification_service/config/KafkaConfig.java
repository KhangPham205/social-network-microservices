package com.socialnetwork.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer/producer factories come from {@code spring.kafka.*}; this only adds the error handling
 * that Boot's auto-configured listener container factory picks up.
 */
@Configuration
public class KafkaConfig {

  private static final long RETRY_INTERVAL_MS = 1000L;
  private static final long MAX_RETRIES = 3L;

  /** Retries transient failures, then publishes the record to {@code <topic>.DLT}. */
  @Bean
  public CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);
    var handler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }
}
