package com.socialnetwork.media_service.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * The Milvus client opens its gRPC channel in the constructor, so the bean is lazy: the service
 * starts even when Milvus is down and the connection is attempted on first use.
 */
@Configuration
public class MilvusConfig {

  @Bean
  @Lazy
  public MilvusServiceClient milvusServiceClient(MilvusProperties properties) {
    return new MilvusServiceClient(ConnectParam.newBuilder().withUri(properties.uri()).build());
  }
}
