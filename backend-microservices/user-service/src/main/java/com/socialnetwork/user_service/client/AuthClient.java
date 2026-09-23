package com.socialnetwork.user_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.user_service.dto.AuthCredentialDto;
import com.socialnetwork.user_service.dto.UpdateRoleStatusRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/** auth-service internal API, protected by the shared internal token. */
@HttpExchange(ApiConstants.AUTH + ApiConstants.INTERNAL)
public interface AuthClient {

  @PostExchange("/credentials/batch")
  List<AuthCredentialDto> getCredentialsBatch(@RequestBody List<Long> ids);

  @PutExchange("/credentials/{id}")
  void updateRoleAndStatus(
      @PathVariable("id") Long id, @RequestBody UpdateRoleStatusRequest request);
}
