package com.socialnetwork.user_service.service.impl;

import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ConflictException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.client.AuthClient;
import com.socialnetwork.user_service.dto.AdminUpdateUserRequest;
import com.socialnetwork.user_service.dto.AdminUserViewDto;
import com.socialnetwork.user_service.dto.AuthCredentialDto;
import com.socialnetwork.user_service.dto.FollowResponse;
import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UpdateProfileRequest;
import com.socialnetwork.user_service.dto.UpdateRoleStatusRequest;
import com.socialnetwork.user_service.dto.UserModerationDto;
import com.socialnetwork.user_service.dto.UserProfileDto;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.event.EventPublisher;
import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.UserInfo;
import com.socialnetwork.user_service.model.UserRela;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRelaRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.service.UserService;
import com.socialnetwork.user_service.utils.BlockUtils;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final UserRelaRepository userRelaRepository;
  private final FriendshipRepository friendshipRepository;
  private final AuthClient authClient;
  private final BlockUtils blockUtils;
  private final EventPublisher eventPublisher;

  // ---------------------------------------------------------------- profile

  @Override
  @Transactional
  public UserSummary createDefaultProfile(Long accountId, String displayName) {
    return userRepository
        .findById(accountId)
        .map(
            existing -> {
              log.info("Profile {} already exists, keeping it", accountId);
              return toSummary(existing);
            })
        .orElseGet(
            () -> {
              User user = User.builder().id(accountId).displayName(displayName).build();
              user.setUserInfo(UserInfo.builder().user(user).build());
              User saved = userRepository.save(user);
              log.info("Created profile {}", accountId);
              eventPublisher.publishProfileUpdated(
                  saved.getId(), saved.getDisplayName(), saved.getAvatarUrl());
              return toSummary(saved);
            });
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileDto getProfile(Long userId) {
    return toProfileDto(findUser(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileDto getVisibleProfile(Long userId) {
    requireVisible(userId);
    return getProfile(userId);
  }

  @Override
  @Transactional
  public UserProfileDto updateMyProfile(UpdateProfileRequest request) {
    Long myId = SecurityUtils.getCurrentUserId();
    User user = findUser(myId);

    if (request.getDisplayName() != null) {
      user.setDisplayName(request.getDisplayName());
    }
    if (user.getUserInfo() == null) {
      user.setUserInfo(UserInfo.builder().user(user).build());
    }
    if (request.getBio() != null) {
      user.getUserInfo().setBio(request.getBio());
    }
    if (request.getFavorites() != null) {
      user.getUserInfo().setFavorites(request.getFavorites());
    }
    if (request.getDateOfBirth() != null) {
      user.getUserInfo().setDateOfBirth(request.getDateOfBirth());
    }

    User saved = userRepository.save(user);
    eventPublisher.publishProfileUpdated(
        saved.getId(), saved.getDisplayName(), saved.getAvatarUrl());
    return toProfileDto(saved);
  }

  // ---------------------------------------------------------------- search & lists

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> searchUsers(String filter, Pageable pageable) {
    Long viewerId = SecurityUtils.getCurrentUserId();
    Set<Long> hidden = blockUtils.getAllBlockedIds(viewerId);

    Specification<User> spec =
        (root, query, cb) ->
            hidden.isEmpty()
                ? cb.notEqual(root.get("id"), viewerId)
                : cb.and(
                    cb.notEqual(root.get("id"), viewerId), cb.not(root.get("id").in(hidden)));

    Specification<User> filterSpec = buildFilterSpec(filter);
    if (filterSpec != null) {
      spec = spec.and(filterSpec);
    }
    return executePagedQuery(spec, pageable, viewerId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFollowersPaged(Long userId, String filter, Pageable pageable) {
    Long viewerId = SecurityUtils.getCurrentUserId();

    // Users whose follow edge points at userId.
    Specification<User> spec =
        (root, query, cb) -> {
          Subquery<Long> subquery = query.subquery(Long.class);
          Root<UserRela> relaRoot = subquery.from(UserRela.class);
          subquery.select(relaRoot.get("follower").get("id"));
          subquery.where(cb.equal(relaRoot.get("following").get("id"), userId));
          return root.get("id").in(subquery);
        };

    return executePagedQuery(withFilter(spec, filter), pageable, viewerId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFollowingPaged(Long userId, String filter, Pageable pageable) {
    Long viewerId = SecurityUtils.getCurrentUserId();

    // Users userId points at.
    Specification<User> spec =
        (root, query, cb) -> {
          Subquery<Long> subquery = query.subquery(Long.class);
          Root<UserRela> relaRoot = subquery.from(UserRela.class);
          subquery.select(relaRoot.get("following").get("id"));
          subquery.where(cb.equal(relaRoot.get("follower").get("id"), userId));
          return root.get("id").in(subquery);
        };

    return executePagedQuery(withFilter(spec, filter), pageable, viewerId);
  }

  // ---------------------------------------------------------------- follows

  @Override
  @Transactional
  public FollowResponse followUser(Long targetId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    if (currentUserId.equals(targetId)) {
      throw new BadRequestException("You cannot follow yourself");
    }
    if (blockUtils.isBlockedEitherWay(currentUserId, targetId)) {
      throw new BadRequestException("You cannot follow this user");
    }

    // A proxy is enough for the owning side; only the target has to be proven to exist.
    User follower = userRepository.getReferenceById(currentUserId);
    User following = findUser(targetId);

    if (userRelaRepository.existsByFollowerAndFollowing(follower, following)) {
      throw new ConflictException("You are already following this user");
    }
    userRelaRepository.save(UserRela.builder().follower(follower).following(following).build());
    return new FollowResponse("Followed successfully", true);
  }

  @Override
  @Transactional
  public FollowResponse unfollowUser(Long targetId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    if (currentUserId.equals(targetId)) {
      throw new BadRequestException("You cannot unfollow yourself");
    }

    User follower = userRepository.getReferenceById(currentUserId);
    User following = findUser(targetId);

    if (!userRelaRepository.existsByFollowerAndFollowing(follower, following)) {
      throw new BadRequestException("You are not following this user");
    }
    userRelaRepository.deleteByFollowerAndFollowing(follower, following);
    return new FollowResponse("Unfollowed successfully", false);
  }

  // ---------------------------------------------------------------- relations

  @Override
  @Transactional(readOnly = true)
  public UserRelationDto getRelationWithUser(Long userId) {
    Long viewerId = requireVisible(userId);
    User target = findUser(userId);
    return mapToRelationDto(viewerId, target);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRelationDto> getRelationsWithUsers(List<Long> targetIds) {
    Long viewerId = SecurityUtils.getCurrentUserId();
    List<User> targets = userRepository.findAllById(targetIds);
    Map<Long, UserRelationDto> relations = mapToRelationDtos(viewerId, targets);
    return targets.stream().map(u -> relations.get(u.getId())).toList();
  }

  // ---------------------------------------------------------------- admin

  @Override
  @Transactional(readOnly = true)
  public PageVO<AdminUserViewDto> getAllUsersForAdmin(String filter, Pageable pageable) {
    Specification<User> spec = buildFilterSpec(filter);
    if (spec == null) {
      spec = (root, query, cb) -> cb.conjunction();
    }

    Page<User> page = userRepository.findAll(spec, pageable);
    if (page.isEmpty()) {
      return PageVO.emptyPage(page);
    }

    Map<Long, AuthCredentialDto> credentials =
        authClient.getCredentialsBatch(page.getContent().stream().map(User::getId).toList()).stream()
            .collect(Collectors.toMap(AuthCredentialDto::getId, c -> c, (a, b) -> a));

    return PageVO.from(page, user -> toAdminViewDto(user, credentials.get(user.getId())));
  }

  @Override
  @Transactional
  public AdminUserViewDto updateUserAsAdmin(Long userId, AdminUpdateUserRequest request) {
    User user = findUser(userId);

    if (request.getDisplayName() != null) {
      user.setDisplayName(request.getDisplayName());
    }
    if (request.getBio() != null) {
      if (user.getUserInfo() == null) {
        user.setUserInfo(UserInfo.builder().user(user).build());
      }
      user.getUserInfo().setBio(request.getBio());
    }
    User saved = userRepository.save(user);

    // Roles and status live in auth-service.
    if (request.getRoles() != null || request.getStatus() != null) {
      authClient.updateRoleAndStatus(
          userId, new UpdateRoleStatusRequest(request.getStatus(), request.getRoles()));
    }

    eventPublisher.publishProfileUpdated(
        saved.getId(), saved.getDisplayName(), saved.getAvatarUrl());
    return toAdminViewDto(saved, findCredential(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public AdminUserViewDto getUserByIdAsAdmin(Long userId) {
    return toAdminViewDto(findUser(userId), findCredential(userId));
  }

  // ---------------------------------------------------------------- internal API

  @Override
  @Transactional(readOnly = true)
  public UserModerationDto getUserForModeration(Long userId) {
    return toModerationDto(findUser(userId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserModerationDto> getUsersByIds(List<Long> ids) {
    return userRepository.findAllById(ids).stream().map(this::toModerationDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSummary> getSummaries(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return userRepository.findAllById(ids).stream().map(this::toSummary).toList();
  }

  // ---------------------------------------------------------------- helpers

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  /** Blocked users must not even learn that the other one exists, hence 404 and not 403. */
  private Long requireVisible(Long userId) {
    Long viewerId = SecurityUtils.getCurrentUserId();
    if (!viewerId.equals(userId) && blockUtils.isBlockedEitherWay(viewerId, userId)) {
      throw new ResourceNotFoundException("User not found");
    }
    return viewerId;
  }

  private AuthCredentialDto findCredential(Long userId) {
    return authClient.getCredentialsBatch(List.of(userId)).stream().findFirst().orElse(null);
  }

  private Specification<User> withFilter(Specification<User> spec, String filter) {
    Specification<User> filterSpec = buildFilterSpec(filter);
    return filterSpec == null ? spec : spec.and(filterSpec);
  }

  /** RSQL when the expression looks like RSQL, plain keyword search otherwise. */
  private Specification<User> buildFilterSpec(String filter) {
    if (!StringUtils.hasText(filter)) {
      return null;
    }
    String trimmed = filter.trim();

    if (trimmed.contains("==") || trimmed.contains("=like=")) {
      try {
        return RSQLJPASupport.toSpecification(trimmed);
      } catch (Exception e) {
        log.warn("Ignoring invalid RSQL filter: {}", trimmed);
      }
    }

    String likeFilter = "%" + trimmed.toLowerCase() + "%";
    return (root, query, cb) -> {
      query.distinct(true);
      var userInfoJoin = root.join("userInfo", JoinType.LEFT);
      return cb.or(
          cb.like(cb.lower(root.get("displayName")), likeFilter),
          cb.like(cb.lower(userInfoJoin.get("bio")), likeFilter),
          cb.like(cb.lower(userInfoJoin.get("favorites")), likeFilter));
    };
  }

  private PageVO<UserRelationDto> executePagedQuery(
      Specification<User> spec, Pageable pageable, Long viewerId) {
    Page<User> page = userRepository.findAll(spec, pageable);
    Map<Long, UserRelationDto> relations = mapToRelationDtos(viewerId, page.getContent());
    return PageVO.from(page, user -> relations.get(user.getId()));
  }

  /** One pass over the page: three bulk queries instead of three queries per row. */
  private Map<Long, UserRelationDto> mapToRelationDtos(Long viewerId, List<User> targets) {
    if (targets.isEmpty()) {
      return Collections.emptyMap();
    }
    List<Long> targetIds = targets.stream().map(User::getId).toList();

    Set<Long> following = userRelaRepository.findFollowingIdsByViewerAndTargets(viewerId, targetIds);
    Set<Long> followers = userRelaRepository.findFollowerIdsByViewerAndTargets(viewerId, targetIds);

    Map<Long, FriendshipResponse> friendships = new HashMap<>();
    for (Friendship f : friendshipRepository.findFriendshipsBetween(viewerId, targetIds)) {
      Long otherId =
          f.getSender().getId().equals(viewerId) ? f.getReceiver().getId() : f.getSender().getId();
      friendships.putIfAbsent(otherId, FriendshipResponse.from(f));
    }

    return targets.stream()
        .collect(
            Collectors.toMap(
                User::getId,
                target ->
                    toRelationDto(
                        target,
                        following.contains(target.getId()),
                        followers.contains(target.getId()),
                        friendships.getOrDefault(
                            target.getId(), FriendshipResponse.none(viewerId, target.getId())))));
  }

  private UserRelationDto mapToRelationDto(Long viewerId, User target) {
    User viewer = userRepository.getReferenceById(viewerId);
    boolean isFollowing = userRelaRepository.existsByFollowerAndFollowing(viewer, target);
    boolean isFollowedBy = userRelaRepository.existsByFollowerAndFollowing(target, viewer);

    FriendshipResponse friendship =
        friendshipRepository
            .findBySenderAndReceiver(viewer, target)
            .or(() -> friendshipRepository.findBySenderAndReceiver(target, viewer))
            .map(FriendshipResponse::from)
            .orElseGet(() -> FriendshipResponse.none(viewerId, target.getId()));

    return toRelationDto(target, isFollowing, isFollowedBy, friendship);
  }

  private UserRelationDto toRelationDto(
      User target, boolean isFollowing, boolean isFollowedBy, FriendshipResponse friendship) {
    UserInfo info = target.getUserInfo();
    return UserRelationDto.builder()
        .id(target.getId())
        .displayName(target.getDisplayName())
        .avatarUrl(target.getAvatarUrl())
        .bio(info != null ? info.getBio() : null)
        .favorites(info != null ? info.getFavorites() : null)
        .dateOfBirth(info != null ? info.getDateOfBirth() : null)
        .joinedAt(target.getCreatedAt())
        .isFollowing(isFollowing)
        .isFollowedBy(isFollowedBy)
        .friendship(friendship)
        .build();
  }

  private UserProfileDto toProfileDto(User user) {
    UserInfo info = user.getUserInfo();
    return UserProfileDto.builder()
        .id(user.getId())
        .displayName(user.getDisplayName())
        .avatarUrl(user.getAvatarUrl())
        .bio(info != null ? info.getBio() : null)
        .favorites(info != null ? info.getFavorites() : null)
        .dateOfBirth(info != null ? info.getDateOfBirth() : null)
        .joinedAt(user.getCreatedAt())
        .build();
  }

  private UserModerationDto toModerationDto(User user) {
    return UserModerationDto.builder()
        .id(user.getId())
        .displayName(user.getDisplayName())
        .avatarUrl(user.getAvatarUrl())
        .bio(user.getUserInfo() != null ? user.getUserInfo().getBio() : null)
        .createdAt(user.getCreatedAt())
        .lastActiveAt(user.getLastActiveAt())
        .build();
  }

  private UserSummary toSummary(User user) {
    return new UserSummary(user.getId(), user.getDisplayName(), user.getAvatarUrl());
  }

  private AdminUserViewDto toAdminViewDto(User user, AuthCredentialDto credential) {
    AdminUserViewDto dto = new AdminUserViewDto();
    dto.setId(user.getId());
    dto.setDisplayName(user.getDisplayName());
    dto.setAvatarUrl(user.getAvatarUrl());

    if (user.getUserInfo() != null) {
      dto.setBio(user.getUserInfo().getBio());
      dto.setDateOfBirth(user.getUserInfo().getDateOfBirth());
      dto.setFavorites(user.getUserInfo().getFavorites());
    }
    if (credential != null) {
      dto.setCredentialId(credential.getId());
      dto.setUsername(credential.getUsername());
      dto.setEmail(credential.getEmail());
      dto.setStatus(credential.getStatus());
      dto.setRoles(credential.getRoles());
    }
    return dto;
  }
}
