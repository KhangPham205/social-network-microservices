package com.socialnetwork.user_service.service;

import com.socialnetwork.user_service.dto.*;
import com.socialnetwork.user_service.model.User;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;

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

  @Transactional(readOnly = true)
  PageVO<AdminUserViewDto> getAllUsersForAdmin(String filter, Pageable pageable);

  @Transactional
  AdminUserViewDto updateUserAsAdmin(Long userId, AdminUpdateUserRequest request);

  @Transactional(readOnly = true)
  AdminUserViewDto getUserByIdAsAdmin(Long userId);

  UserRelationDto getRelationWithUser(Long id);

  List<UserRelationDto> getRelationsWithUsers(List<Long> targetIds);

  UserModerationDto getUserForModeration(Long id);

  List<UserModerationDto> getUsersByIds(List<Long> ids);
}
