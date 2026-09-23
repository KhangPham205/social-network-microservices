package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Python moderation model. Errors and timeouts are never swallowed: a failed scan must not be
 * mistaken for clean content, so the exception propagates and Kafka retry/DLT takes over.
 */
@HttpExchange("/moderate")
public interface AiServiceClient {

  @PostExchange
  AiModerationResponse checkToxicity(@RequestBody AiModerationRequest request);
}
