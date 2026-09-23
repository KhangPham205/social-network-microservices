package com.socialnetwork.chat_service.config;

import com.socialnetwork.common.dto.UserSummary;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Caches the user summaries pulled from user-service. In the test profile {@code
 * spring.cache.type=simple} is used and the customizer below is simply never applied.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  /** Name of the cache holding {@link UserSummary} values keyed by user id. */
  public static final String USER_SUMMARIES = "user_summaries";

  private static final Duration TTL = Duration.ofMinutes(5);

  @Bean
  public RedisCacheManagerBuilderCustomizer userSummariesCacheCustomizer(
      ObjectMapper objectMapper) {
    RedisCacheConfiguration configuration =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(TTL)
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new JacksonJsonRedisSerializer<>(objectMapper, UserSummary.class)));
    return builder -> builder.withCacheConfiguration(USER_SUMMARIES, configuration);
  }
}
