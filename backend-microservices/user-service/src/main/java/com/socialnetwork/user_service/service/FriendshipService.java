package com.socialnetwork.user_service.service;

import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import java.util.List;
import org.springframework.data.domain.Pageable;
import com.socialnetwork.common.vo.PageVO;

public interface FriendshipService {
  FriendshipResponse sendRequest(Long userId, Long targetId);

  FriendshipResponse unsendRequest(Long userId, Long targetId);

  FriendshipResponse acceptRequest(Long senderId, Long receiverId);

  FriendshipResponse rejectRequest(Long senderId, Long receiverId);

  FriendshipResponse unfriend(Long userId, Long friendId);

  FriendshipResponse blockUser(Long userId, Long targetId);

  FriendshipResponse unblockUser(Long userId, Long targetId);

  PageVO<UserRelationDto> getFriends(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getPendingRequests(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getSentRequests(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getBlockedUsers(Long userId, String filter, Pageable pageable);

  // Internal APIs cho các service khác (media-service, etc.)
  /** Lấy danh sách ID của tất cả bạn bè và những người đang follow của một user */
  List<Long> getNetworkIds(Long userId);

  /** Kiểm tra xem user1 có phải bạn của user2 không */
  boolean isFriend(Long user1, Long user2);
}
