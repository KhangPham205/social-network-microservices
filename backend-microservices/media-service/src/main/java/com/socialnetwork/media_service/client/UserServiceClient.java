package com.socialnetwork.media_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** user-service internal API; the shared token is added by {@code InternalTokenInterceptor}. */
@HttpExchange(ApiConstants.USERS + ApiConstants.INTERNAL)
public interface UserServiceClient {

  /** Friends and followed users of {@code userId}, used to scope the feed. */
  @GetExchange("/{userId}/network-ids")
  List<Long> getNetworkIds(@PathVariable("userId") Long userId);

  @GetExchange("/check-friendship")
  boolean isFriend(@RequestParam("user1") Long user1, @RequestParam("user2") Long user2);
}
