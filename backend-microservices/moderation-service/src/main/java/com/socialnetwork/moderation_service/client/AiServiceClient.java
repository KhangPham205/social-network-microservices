package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/moderate")
public interface AiServiceClient {

  @PostExchange
  AiModerationResponse checkToxicity(@RequestBody AiModerationRequest request);
}
