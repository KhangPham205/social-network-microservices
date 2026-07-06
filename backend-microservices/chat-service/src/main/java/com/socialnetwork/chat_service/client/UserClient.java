package com.socialnetwork.chat_service.client;

import com.socialnetwork.chat_service.dto.UserProfileDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/users/internal")
public interface UserClient {

  @GetExchange("/{userId}")
  UserProfileDto getUserProfile(@PathVariable("userId") Long userId);
}
