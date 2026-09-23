package com.socialnetwork.user_service.service.impl;

import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.vo.FriendshipStatus;
import com.socialnetwork.common.vo.NotificationType;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.event.EventPublisher;
import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.UserInfo;
import com.socialnetwork.user_service.model.UserRela;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRelaRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.service.FriendshipService;
import com.socialnetwork.user_service.utils.BlockUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
public class FriendshipServiceImpl implements FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final UserRepository userRepository;
  private final UserRelaRepository userRelaRepository;
  private final BlockUtils blockUtils;
  private final EventPublisher eventPublisher;

  // ---------------------------------------------------------------- requests

  @Override
  @Transactional
  public FriendshipResponse sendRequest(Long userId, Long targetId) {
    if (userId.equals(targetId)) {
      throw new BadRequestException("You cannot send a friend request to yourself");
    }
    if (blockUtils.isBlockedEitherWay(userId, targetId)) {
      throw new BadRequestException("Cannot send request - one of you has blocked the other");
    }

    User sender = findUser(userId);
    User receiver = findUser(targetId);
    Optional<Friendship> existing = findBetween(sender, receiver);

    if (existing.isPresent()) {
      Friendship row = existing.get();
      switch (row.getStatus()) {
        case BLOCKED -> throw new BadRequestException("You cannot send a request to a blocked user");
        case PENDING -> throw new BadRequestException("Friend request already pending");
        case FRIEND -> throw new BadRequestException("You are already friends");
        default -> {
          // A leftover REJECTED row: reuse it when the direction matches, drop it otherwise.
          if (row.getSender().getId().equals(userId)) {
            row.setStatus(FriendshipStatus.PENDING);
            friendshipRepository.save(row);
            return sentResponse(userId, targetId, "Friend request re-sent");
          }
          friendshipRepository.delete(row);
          friendshipRepository.flush();
        }
      }
    }

    friendshipRepository.save(
        Friendship.builder()
            .sender(sender)
            .receiver(receiver)
            .status(FriendshipStatus.PENDING)
            .build());
    return sentResponse(userId, targetId, "Friend request sent");
  }

  @Override
  @Transactional
  public FriendshipResponse unsendRequest(Long userId, Long targetId) {
    Friendship row = getPendingBetween(userId, targetId);
    if (!row.getSender().getId().equals(userId)) {
      throw new AccessDeniedException("You cannot unsend a request you did not send");
    }
    friendshipRepository.delete(row);
    return new FriendshipResponse("Friend request unsent", null, userId, targetId);
  }

  @Override
  @Transactional
  public FriendshipResponse acceptRequest(Long requesterId, Long currentUserId) {
    Friendship row = requireReceivedRequest(requesterId, currentUserId);
    if (blockUtils.isBlockedEitherWay(requesterId, currentUserId)) {
      throw new BadRequestException("Cannot accept - one of you has blocked the other");
    }

    row.setStatus(FriendshipStatus.FRIEND);
    friendshipRepository.save(row);

    // Friends follow each other.
    User requester = row.getSender();
    User current = row.getReceiver();
    follow(requester, current);
    follow(current, requester);

    eventPublisher.publishNotification(
        currentUserId, requesterId, NotificationType.FRIEND_ACCEPT, null, null);
    eventPublisher.publishFriendAccepted(requesterId, currentUserId);

    return new FriendshipResponse(
        "Friend request accepted", FriendshipStatus.FRIEND, requesterId, currentUserId);
  }

  @Override
  @Transactional
  public FriendshipResponse rejectRequest(Long requesterId, Long currentUserId) {
    Friendship row = requireReceivedRequest(requesterId, currentUserId);
    friendshipRepository.delete(row);
    return new FriendshipResponse(
        "Friend request rejected", FriendshipStatus.REJECTED, requesterId, currentUserId);
  }

  // ---------------------------------------------------------------- unfriend / block

  @Override
  @Transactional
  public FriendshipResponse unfriend(Long userId, Long friendId) {
    if (userId.equals(friendId)) {
      throw new BadRequestException("You cannot unfriend yourself");
    }

    // Only FRIEND rows: a block either side may hold must survive.
    List<Friendship> friendRows = friendshipRepository.findFriendRowsBetween(userId, friendId);
    if (friendRows.isEmpty()) {
      throw new ResourceNotFoundException("You are not friends with this user");
    }
    friendshipRepository.deleteAll(friendRows);
    userRelaRepository.deleteFollowsBetween(userId, friendId);

    eventPublisher.publishFriendshipDeleted(userId, friendId);
    return new FriendshipResponse(
        "Unfriended successfully", FriendshipStatus.REJECTED, userId, friendId);
  }

  @Override
  @Transactional
  public FriendshipResponse blockUser(Long userId, Long targetId) {
    if (userId.equals(targetId)) {
      throw new BadRequestException("You cannot block yourself");
    }

    User user = findUser(userId);
    User target = findUser(targetId);

    // The caller's own row becomes the block; any PENDING/FRIEND row of the other side goes away,
    // but a BLOCKED row the target holds against the caller is theirs and stays.
    Friendship mine =
        friendshipRepository
            .findBySenderAndReceiver(user, target)
            .orElseGet(() -> Friendship.builder().sender(user).receiver(target).build());
    mine.setStatus(FriendshipStatus.BLOCKED);
    friendshipRepository.save(mine);

    friendshipRepository
        .findBySenderAndReceiver(target, user)
        .filter(row -> row.getStatus() != FriendshipStatus.BLOCKED)
        .ifPresent(friendshipRepository::delete);

    userRelaRepository.deleteFollowsBetween(userId, targetId);

    eventPublisher.publishFriendshipDeleted(userId, targetId);
    return new FriendshipResponse(
        "User blocked successfully", FriendshipStatus.BLOCKED, userId, targetId);
  }

  @Override
  @Transactional
  public FriendshipResponse unblockUser(Long userId, Long targetId) {
    Friendship row =
        friendshipRepository
            .findBySenderAndReceiverAndStatus(
                findUser(userId), findUser(targetId), FriendshipStatus.BLOCKED)
            .orElseThrow(() -> new ResourceNotFoundException("No blocked relationship found"));
    friendshipRepository.delete(row);
    return new FriendshipResponse(
        "User unblocked successfully", FriendshipStatus.REJECTED, userId, targetId);
  }

  // ---------------------------------------------------------------- lists

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFriends(Long userId, String filter, Pageable pageable) {
    User user = findUser(userId);
    Specification<Friendship> spec =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("status"), FriendshipStatus.FRIEND),
                cb.or(cb.equal(root.get("sender"), user), cb.equal(root.get("receiver"), user)),
                otherSideNameLike(root, cb, user, filter));

    Page<Friendship> page = friendshipRepository.findAll(spec, pageable);
    return mapToPageVO(
        userId, page, f -> f.getSender().getId().equals(userId) ? f.getReceiver() : f.getSender());
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getPendingRequests(Long userId, String filter, Pageable pageable) {
    User user = findUser(userId);
    Specification<Friendship> spec =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("receiver"), user),
                cb.equal(root.get("status"), FriendshipStatus.PENDING),
                nameLike(cb, root.get("sender").get("displayName"), filter));

    return mapToPageVO(userId, friendshipRepository.findAll(spec, pageable), Friendship::getSender);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getSentRequests(Long userId, String filter, Pageable pageable) {
    return listBySender(userId, FriendshipStatus.PENDING, filter, pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getBlockedUsers(Long userId, String filter, Pageable pageable) {
    return listBySender(userId, FriendshipStatus.BLOCKED, filter, pageable);
  }

  // ---------------------------------------------------------------- internal API

  @Override
  @Transactional(readOnly = true)
  public List<Long> getNetworkIds(Long userId) {
    User user = findUser(userId);

    Specification<Friendship> spec =
        (root, query, cb) -> {
          Predicate isFriend = cb.equal(root.get("status"), FriendshipStatus.FRIEND);
          Predicate isInvolved =
              cb.or(cb.equal(root.get("sender"), user), cb.equal(root.get("receiver"), user));
          return cb.and(isFriend, isInvolved);
        };

    Set<Long> networkIds = new HashSet<>();
    for (Friendship f : friendshipRepository.findAll(spec)) {
      networkIds.add(
          f.getSender().getId().equals(userId) ? f.getReceiver().getId() : f.getSender().getId());
    }
    userRelaRepository.findByFollower(user).stream()
        .map(rela -> rela.getFollowing().getId())
        .forEach(networkIds::add);

    return new ArrayList<>(networkIds);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isFriend(Long user1, Long user2) {
    return user1.equals(user2) || friendshipRepository.existsActiveFriendship(user1, user2);
  }

  // ---------------------------------------------------------------- helpers

  private User findUser(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  private Optional<Friendship> findBetween(User first, User second) {
    return friendshipRepository
        .findBySenderAndReceiver(first, second)
        .or(() -> friendshipRepository.findBySenderAndReceiver(second, first));
  }

  private Friendship getPendingBetween(Long userId, Long targetId) {
    Friendship row =
        findBetween(findUser(userId), findUser(targetId))
            .orElseThrow(() -> new ResourceNotFoundException("No friend request found"));
    if (row.getStatus() != FriendshipStatus.PENDING) {
      throw new BadRequestException("This friend request is not pending");
    }
    return row;
  }

  /** The request must exist, be pending, and be addressed to the caller. */
  private Friendship requireReceivedRequest(Long requesterId, Long currentUserId) {
    Friendship row = getPendingBetween(requesterId, currentUserId);
    if (!row.getReceiver().getId().equals(currentUserId)) {
      throw new AccessDeniedException("Only the receiver can answer this friend request");
    }
    return row;
  }

  private void follow(User follower, User following) {
    if (!userRelaRepository.existsByFollowerAndFollowing(follower, following)) {
      userRelaRepository.save(UserRela.builder().follower(follower).following(following).build());
    }
  }

  private FriendshipResponse sentResponse(Long userId, Long targetId, String message) {
    eventPublisher.publishNotification(
        userId, targetId, NotificationType.FRIEND_REQUEST, null, null);
    return new FriendshipResponse(message, FriendshipStatus.PENDING, userId, targetId);
  }

  private PageVO<UserRelationDto> listBySender(
      Long userId, FriendshipStatus status, String filter, Pageable pageable) {
    User user = findUser(userId);
    Specification<Friendship> spec =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("sender"), user),
                cb.equal(root.get("status"), status),
                nameLike(cb, root.get("receiver").get("displayName"), filter));

    return mapToPageVO(
        userId, friendshipRepository.findAll(spec, pageable), Friendship::getReceiver);
  }

  private Predicate nameLike(CriteriaBuilder cb, Expression<String> name, String filter) {
    if (!StringUtils.hasText(filter)) {
      return cb.conjunction();
    }
    return cb.like(cb.lower(name), "%" + filter.trim().toLowerCase() + "%");
  }

  /** Matches the display name of whichever side is not the viewer. */
  private Predicate otherSideNameLike(
      Root<Friendship> root, CriteriaBuilder cb, User viewer, String filter) {
    if (!StringUtils.hasText(filter)) {
      return cb.conjunction();
    }
    return cb.or(
        cb.and(
            cb.equal(root.get("sender"), viewer),
            nameLike(cb, root.get("receiver").get("displayName"), filter)),
        cb.and(
            cb.equal(root.get("receiver"), viewer),
            nameLike(cb, root.get("sender").get("displayName"), filter)));
  }

  /** Bulk-loads the relation of the viewer with every user on the page (no per-row queries). */
  private PageVO<UserRelationDto> mapToPageVO(
      Long viewerId, Page<Friendship> page, Function<Friendship, User> targetExtractor) {
    List<User> targets = page.getContent().stream().map(targetExtractor).toList();
    if (targets.isEmpty()) {
      return PageVO.emptyPage(page);
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

    List<UserRelationDto> content =
        targets.stream()
            .map(
                target ->
                    toRelationDto(
                        target,
                        following.contains(target.getId()),
                        followers.contains(target.getId()),
                        friendships.getOrDefault(
                            target.getId(), FriendshipResponse.none(viewerId, target.getId()))))
            .toList();

    return PageVO.<UserRelationDto>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
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
}
