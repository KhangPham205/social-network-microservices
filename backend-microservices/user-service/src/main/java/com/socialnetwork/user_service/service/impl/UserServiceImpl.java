package com.socialnetwork.user_service.service.impl;

import com.socialnetwork.user_service.dto.*;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.UserInfo;
import com.socialnetwork.user_service.model.UserRela;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRelaRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.service.UserService;
import exception.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import utils.SecurityUtils;
import vo.PageVO;
import vo.friendship.FriendshipStatus;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final UserRelaRepository userRelaRepository;
  private final FriendshipRepository friendshipRepository;

  private Long getCurrentUserId() {
    String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
    return Long.parseLong(userIdStr);
  }

  @Override
  @Transactional
  public void createDefaultProfile(Long accountId, String displayName) {
    User user = User.builder().id(accountId).displayName(displayName).build();

    UserInfo userInfo = UserInfo.builder().user(user).build();

    user.setUserInfo(userInfo); // Link 1-1

    userRepository.save(user);
  }

  @Override
  public UserProfileDto getProfile(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    return UserProfileDto.builder()
        .id(user.getId())
        .displayName(user.getDisplayName())
        .avatarUrl(user.getAvatarUrl())
        .bio(user.getUserInfo() != null ? user.getUserInfo().getBio() : null)
        .favorites(user.getUserInfo() != null ? user.getUserInfo().getFavorites() : null)
        .dateOfBirth(user.getUserInfo() != null ? user.getUserInfo().getDateOfBirth() : null)
        .joinedAt(user.getCreatedAt())
        .build();
  }

  // --- 3. Cập nhật Profile ---
  @Override
  @Transactional
  public UserProfileDto updateMyProfile(UpdateProfileRequest request) {
    Long myId = SecurityUtils.getCurrentUserId();
    User user =
        userRepository.findById(myId).orElseThrow(() -> new RuntimeException("User not found"));

    if (request.getDisplayName() != null) {
      user.setDisplayName(request.getDisplayName());
    }

    if (user.getUserInfo() == null) {
      user.setUserInfo(UserInfo.builder().user(user).build());
    }

    if (request.getBio() != null) user.getUserInfo().setBio(request.getBio());
    if (request.getFavorites() != null) user.getUserInfo().setFavorites(request.getFavorites());
    if (request.getDateOfBirth() != null)
      user.getUserInfo().setDateOfBirth(request.getDateOfBirth());

    userRepository.save(user);

    return getProfile(myId); // Gọi lại hàm get để trả về DTO mới nhất
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> searchUsers(String filter, Pageable pageable) {
    // 1. Lấy user hiện tại (thay thế bằng hàm lấy auth context của dự án mới)
    Long viewerId = SecurityUtils.getCurrentUserId();
    ; // getCurrentUser().getId();

    // TODO: Mở lại logic block khi có module Friendship
    // var blockedByMe = blockUtils.getAllBlockedIds(viewerId);
    // var blockedMe = friendshipRepository.findBlockedUserIdsByTarget(viewerId);
    // var totalBlocked = new HashSet<>(blockedByMe);
    // totalBlocked.addAll(blockedMe);

    // 2. Build Specification tĩnh (Loại bỏ chính mình và những người bị block)
    Specification<User> spec =
        (root, query, cb) ->
            cb.and(
                cb.notEqual(root.get("id"), viewerId)
                // TODO: Mở lại khi có totalBlocked
                // totalBlocked.isEmpty() ? cb.conjunction() :
                // cb.not(root.get("id").in(totalBlocked))
                );

    // 3. Build Specification động từ RSQL hoặc Keyword
    if (StringUtils.hasText(filter)) {
      if (filter.contains("==") || filter.contains("=like=")) {
        spec = spec.and(io.github.perplexhub.rsql.RSQLJPASupport.toSpecification(filter));
      } else {
        String likeFilter = "%" + filter.trim().toLowerCase() + "%";
        Specification<User> keywordSpec =
            (root, query, cb) ->
                cb.or(
                    cb.like(cb.lower(root.get("displayName")), likeFilter),
                    // Dùng LEFT JOIN để tránh lỗi mất data nếu user không có credential/userInfo
                    cb.like(
                        cb.lower(root.join("credential", JoinType.LEFT).get("username")),
                        likeFilter),
                    cb.like(cb.lower(root.join("userInfo", JoinType.LEFT).get("bio")), likeFilter),
                    cb.like(
                        cb.lower(root.join("userInfo", JoinType.LEFT).get("favorites")),
                        likeFilter));
        spec = spec.and(keywordSpec);
      }
    }

    // 4. Query DB
    Page<User> page = userRepository.findAll(spec, pageable);

    List<User> targets = page.getContent();

    Map<Long, UserRelationDto> relationDtos = mapPageToRelationDtos(viewerId, targets);

    List<UserRelationDto> content =
        targets.stream()
            .map(
                u -> {
                  UserRelationDto dto = relationDtos.get(u.getId());

                  if (dto == null) {
                    dto =
                        UserRelationDto.builder()
                            .id(u.getId())
                            .displayName(u.getDisplayName())
                            .avatarUrl(u.getAvatarUrl())
                            .bio(u.getUserInfo() != null ? u.getUserInfo().getBio() : null)
                            .favorites(
                                u.getUserInfo() != null ? u.getUserInfo().getFavorites() : null)
                            .dateOfBirth(
                                u.getUserInfo() != null ? u.getUserInfo().getDateOfBirth() : null)
                            .joinedAt(u.getCreatedAt())
                            .isFollowing(false)
                            .isFollowedBy(false)
                            .friendship(
                                FriendshipResponse.builder()
                                    .status(FriendshipStatus.NONE)
                                    .senderId(viewerId)
                                    .receiverId(u.getId())
                                    .build())
                            .build();
                  } else {
                    if (dto.getFriendship() == null) {
                      dto.setFriendship(
                          FriendshipResponse.builder().status(FriendshipStatus.NONE).build());
                    } else if (dto.getFriendship().getStatus() == null) {
                      dto.getFriendship().setStatus(FriendshipStatus.NONE);
                    }
                  }

                  return dto;
                })
            .toList();
    // 5. Return standard PageVO
    return PageVO.<UserRelationDto>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  @Override
  @Transactional
  public FollowResponse followUser(Long targetId) {
    // TODO: Thay bằng hàm lấy user ID từ Security Context của dự án mới
    Long currentUserId = SecurityUtils.getCurrentUserId();
    System.out.println("Current: " + currentUserId);

    if (currentUserId.equals(targetId)) {
      // Ném exception tuỳ chỉnh của dự án bạn (BadRequestException)
      throw new IllegalArgumentException("You cannot follow yourself");
    }

    // Dùng getReferenceById để lấy Proxy, không tốn câu SELECT xuống DB
    User follower = userRepository.getReferenceById(currentUserId);

    // Target User thì phải findById để check xem có tồn tại thật không
    User following =
        userRepository
            .findById(targetId)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Target user not found")); // Thay bằng ResourceNotFoundException

    boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
    if (exists) {
      throw new IllegalArgumentException("Already following");
    }

    UserRela rela = UserRela.builder().follower(follower).following(following).build();

    userRelaRepository.save(rela);

    return new FollowResponse("Followed successfully", true);
  }

  @Override
  @Transactional
  public FollowResponse unfollowUser(Long targetId) {
    // TODO: Thay bằng hàm lấy user ID từ Security Context
    Long currentUserId = SecurityUtils.getCurrentUserId();

    if (currentUserId.equals(targetId)) {
      throw new IllegalArgumentException("You cannot unfollow yourself");
    }

    User follower = userRepository.getReferenceById(currentUserId);

    User following =
        userRepository
            .findById(targetId)
            .orElseThrow(() -> new RuntimeException("Target user not found"));

    boolean exists = userRelaRepository.existsByFollowerAndFollowing(follower, following);
    if (!exists) {
      throw new IllegalArgumentException("You are not following this user");
    }

    userRelaRepository.deleteByFollowerAndFollowing(follower, following);

    return new FollowResponse("Unfollowed successfully", false);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFollowersPaged(
      Long targetId, String filter, Pageable pageable) {
    // Lấy viewerId để check quan hệ xem TÔI có follow người trong danh sách này không
    Long viewerId = SecurityUtils.getCurrentUserId();
    ; // TODO: getCurrentUser().getId()

    // Build Specification: Tìm các User có ID nằm trong tập hợp những người follow targetId
    Specification<User> spec =
        (root, query, cb) -> {
          Subquery<Long> subquery = query.subquery(Long.class);
          Root<UserRela> relaRoot = subquery.from(UserRela.class);
          // Lấy ID của người đi follow (follower)
          subquery.select(relaRoot.get("follower").get("id"));
          // Điều kiện: người được follow (following) chính là targetId
          subquery.where(cb.equal(relaRoot.get("following").get("id"), targetId));

          return root.get("id").in(subquery);
        };

    return executePagedQuery(spec, filter, pageable, viewerId);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFollowingPaged(
      Long targetId, String filter, Pageable pageable) {
    Long viewerId = SecurityUtils.getCurrentUserId();
    ; // TODO: getCurrentUser().getId()

    // Build Specification: Tìm các User có ID nằm trong tập hợp những người mà targetId đang follow
    Specification<User> spec =
        (root, query, cb) -> {
          Subquery<Long> subquery = query.subquery(Long.class);
          Root<UserRela> relaRoot = subquery.from(UserRela.class);
          // Lấy ID của người được follow (following)
          subquery.select(relaRoot.get("following").get("id"));
          // Điều kiện: người đi follow (follower) chính là targetId
          subquery.where(cb.equal(relaRoot.get("follower").get("id"), targetId));

          return root.get("id").in(subquery);
        };

    return executePagedQuery(spec, filter, pageable, viewerId);
  }

  @Override
  public User getCurrentUser() {
    // 1. Lấy ID từ Utils
    Long userId = SecurityUtils.getCurrentUserId();

    // 2. Query DB để lấy nguyên object User
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  @Override
  public UserRelationDto getRelationWithUser(Long targetId) {
    User current = getCurrentUser();
    User target =
        userRepository
            .findById(targetId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    return mapToRelationDto(current, target);
  }

  /** Hàm map tạm thời khi chưa có module Friendship/Follow */
  private Map<Long, UserRelationDto> mapPageToRelationDtos(Long viewerId, List<User> targets) {
    if (targets.isEmpty()) {
      return java.util.Collections.emptyMap();
    }

    // 1. Gom tất cả ID của users trong page hiện tại
    List<Long> targetIds = targets.stream().map(User::getId).toList();

    // 2. Query 1 phát lấy tất cả những người mình đang follow trong list này
    Set<Long> myFollowingIds =
        userRelaRepository.findFollowingIdsByViewerAndTargets(viewerId, targetIds);

    // 3. Query 1 phát lấy tất cả những người đang follow mình trong list này
    Set<Long> myFollowerIds =
        userRelaRepository.findFollowerIdsByViewerAndTargets(viewerId, targetIds);

    // 4. Map data vào DTO
    return targets.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                User::getId,
                target ->
                    UserRelationDto.builder()
                        .id(target.getId())
                        .displayName(target.getDisplayName())
                        .avatarUrl(target.getAvatarUrl())
                        // Map thêm các trường bio, dob... nếu cần từ target.getUserInfo()

                        // Check xem ID của họ có nằm trong Set mình vừa lấy lên không
                        .isFollowing(myFollowingIds.contains(target.getId()))
                        .isFollowedBy(myFollowerIds.contains(target.getId()))

                        // TODO: Trạng thái Friendship (kết bạn) sẽ cập nhật sau khi có module
                        // Friendship
                        .friendship(null)
                        .build()));
  }

  /** Hàm thực thi query chung, map data và trả về PageVO */
  private PageVO<UserRelationDto> executePagedQuery(
      Specification<User> baseSpec, String filter, Pageable pageable, Long viewerId) {
    // Gắn thêm filter tìm kiếm (nếu có)
    Specification<User> filterSpec = buildFilterSpec(filter);
    if (filterSpec != null) {
      baseSpec = baseSpec.and(filterSpec);
    }

    Page<User> page = userRepository.findAll(baseSpec, pageable);
    List<User> targets = page.getContent();

    // TÁI SỬ DỤNG hàm N+1 mà chúng ta đã viết hôm trước!
    Map<Long, UserRelationDto> relationDtos = mapPageToRelationDtos(viewerId, targets);

    List<UserRelationDto> content = targets.stream().map(u -> relationDtos.get(u.getId())).toList();

    return PageVO.<UserRelationDto>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  /** Hàm tách logic Search động ra để dùng chung cho cả searchUsers, getFollowers, getFollowing */
  private Specification<User> buildFilterSpec(String filter) {
    if (!StringUtils.hasText(filter)) {
      return null;
    }

    if (filter.contains("==") || filter.contains("=like=")) {
      return io.github.perplexhub.rsql.RSQLJPASupport.toSpecification(filter);
    } else {
      String likeFilter = "%" + filter.trim().toLowerCase() + "%";
      return (root, query, cb) ->
          cb.or(
              cb.like(cb.lower(root.get("displayName")), likeFilter),
              cb.like(cb.lower(root.join("credential", JoinType.LEFT).get("username")), likeFilter),
              cb.like(cb.lower(root.join("userInfo", JoinType.LEFT).get("bio")), likeFilter),
              cb.like(cb.lower(root.join("userInfo", JoinType.LEFT).get("favorites")), likeFilter));
    }
  }

  private UserRelationDto mapToRelationDto(User viewer, User target) {
    boolean isFollowing = userRelaRepository.existsByFollowerAndFollowing(viewer, target);
    boolean isFollowedBy = userRelaRepository.existsByFollowerAndFollowing(target, viewer);

    var friendship =
        friendshipRepository
            .findBySenderAndReceiver(viewer, target)
            .or(() -> friendshipRepository.findBySenderAndReceiver(target, viewer))
            .map(
                f ->
                    FriendshipResponse.builder()
                        .status(f.getStatus())
                        .senderId(f.getSender().getId())
                        .receiverId(f.getReceiver().getId())
                        .build())
            .orElse(FriendshipResponse.builder().build()); // Empty response if no friendship exists

    UserProfileDto base =
        UserRelationDto.builder()
            .id(target.getId())
            .displayName(target.getDisplayName())
            .avatarUrl(target.getAvatarUrl())
            .bio(target.getUserInfo() != null ? target.getUserInfo().getBio() : null)
            .favorites(target.getUserInfo() != null ? target.getUserInfo().getFavorites() : null)
            .dateOfBirth(
                target.getUserInfo() != null ? target.getUserInfo().getDateOfBirth() : null)
            .build();

    return UserRelationDto.builder()
        .id(base.getId())
        .displayName(base.getDisplayName())
        .avatarUrl(base.getAvatarUrl())
        .bio(base.getBio())
        .favorites(base.getFavorites())
        .dateOfBirth(base.getDateOfBirth())
        .isFollowing(isFollowing)
        .isFollowedBy(isFollowedBy)
        .friendship(friendship) // Use the properly built friendship response
        .build();
  }
}
