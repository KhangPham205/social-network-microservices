package com.socialnetwork.moderation_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The whole context must start offline: H2, Eureka off, Kafka listeners not started. */
@SpringBootTest
@ActiveProfiles("test")
class ModerationServiceApplicationTests {

  @Test
  void contextLoads() {}
}
