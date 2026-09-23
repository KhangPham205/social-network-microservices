package com.socialnetwork.moderation_service.client;

import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.moderation_service.dto.external.UserExternalDto;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** user-service internal API: profiles for the admin views. */
@HttpExchange(ApiConstants.USERS + ApiConstants.INTERNAL)
public interface UserClient {

  @GetExchange("/{id}/admin-detail")
  UserExternalDto getUserDetail(@PathVariable("id") Long id);

  @PostExchange("/batch")
  List<UserExternalDto> getUsersByIds(@RequestBody List<Long> ids);

  /** Minimal identities (display name + avatar) for enriching lists. */
  @PostExchange("/summaries")
  List<UserSummary> getSummaries(@RequestBody List<Long> ids);
}
