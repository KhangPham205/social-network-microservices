package com.socialnetwork.chat_service.service;

import com.socialnetwork.common.dto.UserSummary;
import java.util.Collection;
import java.util.Map;

/** Read-through view of user-service identities, batched per request and cached. */
public interface UserDirectory {

  /** Never misses a requested id: unknown users get a placeholder summary. */
  Map<Long, UserSummary> getSummaries(Collection<Long> userIds);

  UserSummary getSummary(Long userId);
}
