package com.socialnetwork.chat_service;

import com.socialnetwork.chat_service.repository.mongo.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the whole context against H2. The Mongo repository is mocked; the Mongo client itself is
 * created lazily by the driver, so no server is required.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatServiceApplicationTests {

  @MockitoBean private ChatMessageRepository chatMessageRepository;

  @Test
  void contextLoads() {}
}
