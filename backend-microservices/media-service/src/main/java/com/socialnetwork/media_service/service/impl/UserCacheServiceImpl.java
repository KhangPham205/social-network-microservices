package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheServiceImpl implements UserCacheService {

  private final UserCacheRepository userCacheRepository;

  @Override
  @Transactional
  public void onUserCreated(UserCreatedEvent event) {
    if (event.accountId() == null) {
      throw new IllegalArgumentException("UserCreatedEvent.accountId is required");
    }
    if (userCacheRepository.existsById(event.accountId())) {
      log.debug("User {} already cached, ignoring UserCreatedEvent", event.accountId());
      return;
    }
    userCacheRepository.save(
        UserCache.builder().id(event.accountId()).displayName(event.username()).build());
    log.info("User {} cached from UserCreatedEvent", event.accountId());
  }

  @Override
  @Transactional
  public void onProfileUpdated(ProfileUpdatedEvent event) {
    if (event.accountId() == null) {
      throw new IllegalArgumentException("ProfileUpdatedEvent.accountId is required");
    }
    UserCache cache =
        userCacheRepository
            .findById(event.accountId())
            .orElseGet(() -> UserCache.builder().id(event.accountId()).build());
    if (event.displayName() != null) {
      cache.setDisplayName(event.displayName());
    }
    if (event.avatarUrl() != null) {
      cache.setAvatarUrl(event.avatarUrl());
    }
    userCacheRepository.save(cache);
    log.info("User {} cache refreshed from ProfileUpdatedEvent", event.accountId());
  }
}
