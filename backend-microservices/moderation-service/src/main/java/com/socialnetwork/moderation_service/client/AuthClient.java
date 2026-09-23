package com.socialnetwork.moderation_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.moderation_service.dto.external.AuthExternalDto;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** auth-service internal API: credentials (e-mail, username, account status). */
@HttpExchange(ApiConstants.AUTH + ApiConstants.INTERNAL)
public interface AuthClient {

  @GetExchange("/credentials/{id}")
  AuthExternalDto getCredential(@PathVariable("id") Long id);

  @PostExchange("/credentials/batch")
  List<AuthExternalDto> getCredentialsByIds(@RequestBody List<Long> ids);
}
