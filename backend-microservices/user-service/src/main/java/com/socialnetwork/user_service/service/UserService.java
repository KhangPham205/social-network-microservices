package com.socialnetwork.user_service.service;

import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.AdminUpdateUserRequest;
import com.socialnetwork.user_service.dto.AdminUserViewDto;
import com.socialnetwork.user_service.dto.FollowResponse;
import com.socialnetwork.user_service.dto.UpdateProfileRequest;
import com.socialnetwork.user_service.dto.UserModerationDto;
import com.socialnetwork.user_service.dto.UserProfileDto;
import com.socialnetwork.user_service.dto.UserRelationDto;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface UserService {

  /**
   * Creates the profile of a freshly registered account. Idempotent: an existing profile is
   * returned untouched so a redelivered {@code UserCreatedEvent} cannot overwrite it.
   */
  UserSummary createDefaultProfile(Long accountId, String displayName);

  /** Raw profile, no visibility check. For internal callers and for the caller's own profile. */
  UserProfileDto getProfile(Long userId);

  /** Profile as seen by the current user: 404 when one of them blocked the other. */
  UserProfileDto getVisibleProfile(Long userId);

  UserProfileDto updateMyProfile(UpdateProfileRequest request);

  PageVO<UserRelationDto> searchUsers(String filter, Pageable pageable);

  FollowResponse followUser(Long targetId);

  FollowResponse unfollowUser(Long targetId);

  PageVO<UserRelationDto> getFollowersPaged(Long userId, String filter, Pageable pageable);

  PageVO<UserRelationDto> getFollowingPaged(Long userId, String filter, Pageable pageable);

  PageVO<AdminUserViewDto> getAllUsersForAdmin(String filter, Pageable pageable);

  AdminUserViewDto updateUserAsAdmin(Long userId, AdminUpdateUserRequest request);

  AdminUserViewDto getUserByIdAsAdmin(Long userId);

  /** Relation between the current user and {@code userId}; 404 when either side blocked. */
  UserRelationDto getRelationWithUser(Long userId);

  List<UserRelationDto> getRelationsWithUsers(List<Long> targetIds);

  UserModerationDto getUserForModeration(Long userId);

  List<UserModerationDto> getUsersByIds(List<Long> ids);

  /** Minimal identities for the read-model caches of the other services. */
  List<UserSummary> getSummaries(List<Long> ids);
}
