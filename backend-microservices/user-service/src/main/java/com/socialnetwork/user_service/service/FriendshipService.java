package com.socialnetwork.user_service.service;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface FriendshipService {

  FriendshipResponse sendRequest(Long userId, Long targetId);

  /** Cancels a request the caller sent. */
  FriendshipResponse unsendRequest(Long userId, Long targetId);

  /** Only the receiver of the request may accept it. */
  FriendshipResponse acceptRequest(Long requesterId, Long currentUserId);

  /** Only the receiver of the request may reject it. */
  FriendshipResponse rejectRequest(Long requesterId, Long currentUserId);

  FriendshipResponse unfriend(Long userId, Long friendId);

  /** Directional: it does not touch a block the other user set on the caller. */
  FriendshipResponse blockUser(Long userId, Long targetId);

  /** Removes only the caller's own block. */
  FriendshipResponse unblockUser(Long userId, Long targetId);

  PageVO<UserRelationDto> getFriends(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getPendingRequests(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getSentRequests(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getBlockedUsers(Long userId, String filter, Pageable pageable);

  /** Friends plus followed users; used by media-service to build a feed. */
  List<Long> getNetworkIds(Long userId);

  boolean isFriend(Long user1, Long user2);
}
