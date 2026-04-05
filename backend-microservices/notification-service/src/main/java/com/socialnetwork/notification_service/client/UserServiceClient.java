package com.socialnetwork.notification_service.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/users/internal")
public interface UserServiceClient {

  @GetExchange("/{userId}/network-ids")
  java.util.List<Long> getNetworkIds(@PathVariable("userId") Long userId);

  @GetExchange("/check-friendship")
  boolean isFriend(
      @org.springframework.web.bind.annotation.RequestParam("user1") Long user1,
      @org.springframework.web.bind.annotation.RequestParam("user2") Long user2);
}
