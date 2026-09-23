package com.socialnetwork.user_service.utils;

import com.socialnetwork.common.vo.FriendshipStatus;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Block lookups shared by the search, profile and friendship flows. */
@Component
@RequiredArgsConstructor
public class BlockUtils {

  private final FriendshipRepository friendshipRepository;

  /** Ids the user blocked plus ids that blocked the user: nobody in this set may be shown. */
  @Transactional(readOnly = true)
  public Set<Long> getAllBlockedIds(Long userId) {
    Set<Long> ids = new HashSet<>(friendshipRepository.findBlockedUserIds(userId));
    ids.addAll(friendshipRepository.findUserIdsBlocking(userId));
    return ids;
  }

  /** True when {@code currentUserId} blocked {@code targetId} (directional). */
  @Transactional(readOnly = true)
  public boolean isBlocked(Long currentUserId, Long targetId) {
    return friendshipRepository.existsBySenderIdAndReceiverIdAndStatus(
        currentUserId, targetId, FriendshipStatus.BLOCKED);
  }

  /** True when either user blocked the other; they must not see each other at all. */
  @Transactional(readOnly = true)
  public boolean isBlockedEitherWay(Long userId, Long targetId) {
    return friendshipRepository.existsBlockBetween(userId, targetId);
  }
}
