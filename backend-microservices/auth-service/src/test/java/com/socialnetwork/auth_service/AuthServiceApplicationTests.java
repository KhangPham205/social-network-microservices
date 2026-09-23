package com.socialnetwork.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Boots the whole context against H2 with Kafka listeners and Eureka switched off. */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

  @Test
  void contextLoads() {}
}
