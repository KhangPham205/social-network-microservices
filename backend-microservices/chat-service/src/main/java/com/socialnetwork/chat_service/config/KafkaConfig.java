package com.socialnetwork.chat_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer and producer factories come from {@code spring.kafka.*}; the only Kafka bean a service
 * declares is the error handler that Boot's listener container factory picks up.
 */
@Configuration
public class KafkaConfig {

  private static final long RETRY_INTERVAL_MS = 1000L;
  private static final long MAX_RETRIES = 3L;

  /** Retries transient failures, then publishes the record to {@code <topic>.DLT}. */
  @Bean
  public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);
    var handler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }
}
