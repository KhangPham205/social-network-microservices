package com.socialnetwork.chat_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.dto.UserSummary;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** user-service internal API, protected by the shared internal token. */
@HttpExchange(ApiConstants.USERS + ApiConstants.INTERNAL)
public interface UserClient {

  /** One round trip for every participant of a conversation. */
  @PostExchange("/summaries")
  List<UserSummary> getSummaries(@RequestBody List<Long> ids);
}
