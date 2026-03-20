package com.socialnetwork.user_service.service;

import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import org.springframework.data.domain.Pageable;
import vo.PageVO;

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
}
