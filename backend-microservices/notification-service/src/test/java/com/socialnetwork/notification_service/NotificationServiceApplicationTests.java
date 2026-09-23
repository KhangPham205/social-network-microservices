package com.socialnetwork.notification_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Boots the whole context offline: H2, Eureka disabled and Kafka listeners not started. */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

  @Test
  void contextLoads() {}
}
