package com.socialnetwork.user_service;

import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The context has to come up without Postgres, Neo4j, Kafka or Eureka. */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

  @MockitoBean private Driver neo4jDriver;

  @MockitoBean private UserNodeRepository userNodeRepository;

  @Test
  void contextLoads() {}
}
