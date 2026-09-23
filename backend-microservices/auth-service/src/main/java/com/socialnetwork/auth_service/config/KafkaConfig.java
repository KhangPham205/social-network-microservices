package com.socialnetwork.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Producer/consumer factories come from {@code spring.kafka.*}. This only adds the shared retry +
 * dead-letter policy: 3 retries one second apart, then the record goes to {@code <topic>.DLT}.
 */
@Configuration
public class KafkaConfig {

  @Bean
  CommonErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }
}
