package com.socialnetwork.chat_service.service.impl;

import com.socialnetwork.chat_service.client.UserClient;
import com.socialnetwork.chat_service.config.CacheConfig;
import com.socialnetwork.chat_service.service.UserDirectory;
import com.socialnetwork.common.dto.UserSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Resolves the cache first and then fetches every remaining id in a single call to user-service, so
 * rendering a conversation costs at most one round trip regardless of the number of participants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedUserDirectory implements UserDirectory {

  private final UserClient userClient;
  private final CacheManager cacheManager;

  @Override
  public Map<Long, UserSummary> getSummaries(Collection<Long> userIds) {
    Map<Long, UserSummary> resolved = new LinkedHashMap<>();
    if (userIds == null || userIds.isEmpty()) {
      return resolved;
    }

    Set<Long> wanted = new LinkedHashSet<>(userIds);
    wanted.remove(null);
    if (wanted.isEmpty()) {
      return resolved;
    }

    Cache cache = cacheManager.getCache(CacheConfig.USER_SUMMARIES);
    List<Long> misses = new ArrayList<>();
    for (Long id : wanted) {
      UserSummary cached = cache == null ? null : cache.get(id, UserSummary.class);
      if (cached != null) {
        resolved.put(id, cached);
      } else {
        misses.add(id);
      }
    }

    if (!misses.isEmpty()) {
      for (UserSummary summary : fetch(misses)) {
        if (summary != null && summary.id() != null) {
          resolved.put(summary.id(), summary);
          if (cache != null) {
            cache.put(summary.id(), summary);
          }
        }
      }
    }

    for (Long id : wanted) {
      resolved.computeIfAbsent(id, CachedUserDirectory::placeholder);
    }
    return resolved;
  }

  @Override
  public UserSummary getSummary(Long userId) {
    return getSummaries(List.of(userId)).get(userId);
  }

  private List<UserSummary> fetch(List<Long> ids) {
    List<UserSummary> summaries = userClient.getSummaries(ids);
    return summaries == null ? List.of() : summaries;
  }

  /** Keeps a deleted or unreachable user from breaking a whole conversation listing. */
  private static UserSummary placeholder(Long id) {
    return new UserSummary(id, "User " + id, null);
  }
}
