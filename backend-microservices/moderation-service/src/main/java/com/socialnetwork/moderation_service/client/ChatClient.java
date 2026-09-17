package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.ModerationMessageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

public interface ChatClient {
  @GetExchange("/api/v1/messages/{id}/from-user")
  Long getMessageOwnerId(@PathVariable("id") String id);

  @GetExchange("/api/v1/messages/{id}/detail")
  ModerationMessageResponse getMessageDetail(@PathVariable("id") String id);

  // Lấy danh sách nhiều tin nhắn cùng lúc
  @PostExchange("/api/v1/messages/batch")
  List<ModerationMessageResponse> getMessagesByIds(@RequestBody List<String> ids);
}
