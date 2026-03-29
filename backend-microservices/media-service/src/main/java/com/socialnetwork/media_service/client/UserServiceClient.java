package com.socialnetwork.media_service.client;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/users/internal")
public interface UserServiceClient {

  @GetExchange("/{userId}/network-ids")
  List<Long> getNetworkIds(@PathVariable("userId") Long userId);

  @GetExchange("/check-friendship")
  boolean isFriend(@RequestParam("user1") Long user1, @RequestParam("user2") Long user2);
}
