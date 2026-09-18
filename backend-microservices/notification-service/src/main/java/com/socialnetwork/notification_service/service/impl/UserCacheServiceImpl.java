package com.socialnetwork.notification_service.service.impl;

import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.notification_service.client.UserServiceClient;
import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.UserCacheRepository;
import com.socialnetwork.notification_service.service.UserCacheService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheServiceImpl implements UserCacheService {

  private final UserCacheRepository userCacheRepository;
  private final UserServiceClient userServiceClient;

  @Override
  @Transactional
  public UserCache getOrFetch(Long userId) {
    return userCacheRepository.findById(userId).orElseGet(() -> fetchAndCache(userId));
  }

  private UserCache fetchAndCache(Long userId) {
    log.info("User {} missing from cache, fetching from user-service", userId);
    List<UserSummary> summaries = userServiceClient.getSummaries(List.of(userId));
    UserSummary summary =
        summaries.stream()
            .filter(s -> userId.equals(s.id()))
            .findFirst()
            .orElseThrow(
                () -> new ResourceNotFoundException("User " + userId + " not found in user-service"));
    return userCacheRepository.save(
        UserCache.builder()
            .id(summary.id())
            .displayName(summary.displayName())
            .avatarUrl(summary.avatarUrl())
            .build());
  }

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
