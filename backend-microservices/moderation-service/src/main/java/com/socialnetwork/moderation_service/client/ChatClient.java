package com.socialnetwork.moderation_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.dto.MessageModerationView;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** chat-service internal API: message bodies and ownership. Message ids are Mongo ObjectIds. */
@HttpExchange(ApiConstants.CHAT + ApiConstants.INTERNAL)
public interface ChatClient {

  @GetExchange("/messages/{id}")
  MessageModerationView getMessage(@PathVariable("id") String id);

  @GetExchange("/messages/{id}/owner-id")
  Long getMessageOwnerId(@PathVariable("id") String id);

  @PostExchange("/messages/batch")
  List<MessageModerationView> getMessagesByIds(@RequestBody List<String> ids);
}
