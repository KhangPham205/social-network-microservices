package com.socialnetwork.auth_service.client;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/users")
public interface UserServiceClient {

  @PostExchange("/internal/create")
  void createEmptyProfile(
      @RequestParam("accountId") Long accountId, @RequestParam("displayName") String displayName);
}
