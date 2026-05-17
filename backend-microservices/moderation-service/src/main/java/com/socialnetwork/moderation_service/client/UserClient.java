package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.external.UserExternalDto;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/users") // Nhớ map bean này trong HttpExchangeConfig nhé
public interface UserClient {

  @GetExchange("/{id}/admin-detail")
  UserExternalDto getUserDetail(@PathVariable("id") Long id);

  // API cực kỳ quan trọng cho Microservices: Lấy 1 lúc nhiều user
  @PostExchange("/batch")
  List<UserExternalDto> getUsersByIds(@RequestBody List<Long> ids);
}
