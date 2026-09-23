package com.socialnetwork.media_service;

import io.milvus.client.MilvusServiceClient;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the whole context offline: H2, Eureka disabled, Kafka listeners not started and every
 * client of an external system replaced by a mock.
 */
@SpringBootTest
@ActiveProfiles("test")
class MediaServiceApplicationTests {

  @MockitoBean private MilvusServiceClient milvusServiceClient;
  @MockitoBean private Driver neo4jDriver;
  @MockitoBean private MinioClient minioClient;

  @Test
  void contextLoads() {}
}
