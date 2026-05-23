package com.socialnetwork.user_service.service;

import com.socialnetwork.user_service.dto.*;
import com.socialnetwork.user_service.model.User;
import org.springframework.data.domain.Pageable;
import vo.PageVO;

import java.util.List;

public interface UserService {
  // Hàm dành cho Auth Service gọi nội bộ khi có user đăng ký mới
  void createDefaultProfile(Long accountId, String displayName);

  // Lấy profile của chính mình hoặc người khác
  UserProfileDto getProfile(Long userId);

  // Cập nhật profile của chính mình
  UserProfileDto updateMyProfile(UpdateProfileRequest request);

  PageVO<UserRelationDto> searchUsers(String filter, Pageable pageable);

  FollowResponse followUser(Long targetId);

  FollowResponse unfollowUser(Long targetId);

  PageVO<UserRelationDto> getFollowersPaged(Long id, String filter, Pageable pageable);

  PageVO<UserRelationDto> getFollowingPaged(Long id, String filter, Pageable pageable);

  User getCurrentUser();

  UserRelationDto getRelationWithUser(Long id);

  UserModerationDto getUserForModeration(Long id);

  List<UserModerationDto> getUsersByIds(List<Long> ids);
}
