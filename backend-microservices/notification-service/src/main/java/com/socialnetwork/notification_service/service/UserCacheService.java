package com.socialnetwork.notification_service.service;

import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.notification_service.model.UserCache;

/** Read model of user identities used to render notification actors. */
public interface UserCacheService {

  /**
   * Returns the cached user, fetching it from user-service and caching it on a miss.
   *
   * @throws com.socialnetwork.common.exception.ResourceNotFoundException when user-service does
   *     not know the id
   */
  UserCache getOrFetch(Long userId);

  /** Inserts a cache row on registration; never overwrites an existing row. */
  void onUserCreated(UserCreatedEvent event);

  /** Creates or updates displayName / avatarUrl. */
  void onProfileUpdated(ProfileUpdatedEvent event);
}
