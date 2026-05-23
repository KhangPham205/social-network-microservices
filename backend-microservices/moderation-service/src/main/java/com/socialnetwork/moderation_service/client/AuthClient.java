package com.socialnetwork.moderation_service.client;

import com.socialnetwork.moderation_service.dto.external.AuthExternalDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import java.util.List;

@HttpExchange("/api/v1/auth/internal")
public interface AuthClient {

  @GetExchange("/credentials/{id}")
  AuthExternalDto getCredential(@PathVariable("id") Long id);

  @PostExchange("/credentials/batch")
  List<AuthExternalDto> getCredentialsByIds(@RequestBody List<Long> ids);
}