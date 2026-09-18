package com.socialnetwork.notification_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.dto.UserSummary;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** user-service internal API (token protected, see CONTRACT section 4). */
@HttpExchange(ApiConstants.USERS + ApiConstants.INTERNAL)
public interface UserServiceClient {

  @PostExchange("/summaries")
  List<UserSummary> getSummaries(@RequestBody List<Long> ids);
}
