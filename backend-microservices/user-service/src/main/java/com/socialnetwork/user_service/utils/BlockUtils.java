package com.socialnetwork.user_service.utils;

import com.socialnetwork.user_service.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vo.friendship.FriendshipStatus;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BlockUtils {

  private final FriendshipRepository friendshipRepository;

  /**
   * Lấy toàn bộ ID những người bị user này chặn
   */
  @Transactional(readOnly = true)
  public Set<Long> getAllBlockedIds(Long userId) {
    return new HashSet<>(friendshipRepository.findBlockedUserIds(userId));
  }

  /**
   * Kiểm tra siêu tốc xem currentUserId có đang chặn targetId hay không.
   * TỐI ƯU: Đẩy thẳng lệnh check xuống Database thay vì kéo List về RAM.
   */
  @Transactional(readOnly = true)
  public boolean isBlocked(Long currentUserId, Long targetId) {
    return friendshipRepository.existsBySenderIdAndReceiverIdAndStatus(
        currentUserId,
        targetId,
        FriendshipStatus.BLOCKED
    );
  }
}
