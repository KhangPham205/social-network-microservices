package com.socialnetwork.media_service.service;

import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;

/** Keeps the local {@code user_caches} read model in sync with auth- and user-service. */
public interface UserCacheService {

  /** Inserts the row on registration; never overwrites an existing one. */
  void onUserCreated(UserCreatedEvent event);

  /** Refreshes displayName / avatarUrl, creating the row when it is missing. */
  void onProfileUpdated(ProfileUpdatedEvent event);
}
