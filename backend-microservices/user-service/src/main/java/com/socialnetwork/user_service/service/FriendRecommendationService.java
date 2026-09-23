package com.socialnetwork.user_service.service;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.UserRelationDto;
import org.springframework.data.domain.Pageable;

public interface FriendRecommendationService {

  /** Friends of friends the user is not connected to yet, most mutual friends first. */
  PageVO<UserRelationDto> getFriendRecommendations(Long userId, Pageable pageable);
}
