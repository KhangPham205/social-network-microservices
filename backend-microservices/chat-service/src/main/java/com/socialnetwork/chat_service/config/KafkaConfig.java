package com.socialnetwork.chat_service.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@EnableKafka
@Configuration
public class KafkaConfig {

  public static final String CHAT_NOTIFICATION_TOPIC = "chat-notification-topic";
  public static final String FRIENDSHIP_EVENTS_TOPIC = "friendship-events";

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Bean
  public NewTopic chatNotificationTopic() {
    return TopicBuilder.name(CHAT_NOTIFICATION_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic friendshipEventsTopic() {
    return TopicBuilder.name(FRIENDSHIP_EVENTS_TOPIC).partitions(3).replicas(1).build();
  }

  @Bean
  public ConsumerFactory<String, Object> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

    props.put(ConsumerConfig.GROUP_ID_CONFIG, "chat-service-group-final");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class);

    props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.socialnetwork.common.events");
    props.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, true);

    props.put(
        JacksonJsonDeserializer.TYPE_MAPPINGS,
        "eventAccept:com.socialnetwork.common.events.FriendAcceptedEvent,eventDelete:com.socialnetwork.common.events.FriendshipDeletedEvent");

    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    return factory;
  }
}
