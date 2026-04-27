package com.socialnetwork.user_service.service.impl;

import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.events.FriendshipAcceptedEvent;
import com.socialnetwork.user_service.events.FriendshipDeletedEvent;
import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.model.UserRela;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRelaRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.service.FriendshipService;
import com.socialnetwork.user_service.utils.BlockUtils;
import events.FriendRequestEvent;
import exception.AccessDeniedException;
import exception.BadRequestException;
import exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;
import vo.friendship.FriendshipStatus;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final UserRepository userRepository;
  private final UserRelaRepository userRelaRepository;
  private final BlockUtils blockUtils;

  // MICROSERVICES: Thay vì Inject trực tiếp NotificationService/ConversationService,
  // chúng ta bắn Event. Một Listener sẽ bắt event này và gửi message qua Kafka.
  private final ApplicationEventPublisher eventPublisher;

  // =================================================================================
  // ACTIONS
  // =================================================================================

  @Override
  @Transactional
  public FriendshipResponse sendRequest(Long userId, Long targetId) {
    if (userId.equals(targetId))
      throw new BadRequestException("You cannot send a friend request to yourself");
    if (blockUtils.isBlocked(userId, targetId) || blockUtils.isBlocked(targetId, userId))
      throw new BadRequestException("Cannot send request — one of you has blocked the other");

    User sender = getUser(userId);
    User receiver = getUser(targetId);

    Optional<Friendship> existing =
        friendshipRepository
            .findBySenderAndReceiver(sender, receiver)
            .or(() -> friendshipRepository.findBySenderAndReceiver(receiver, sender));

    if (existing.isPresent()) {
      Friendship f = existing.get();
      switch (f.getStatus()) {
        case BLOCKED ->
            throw new BadRequestException("You cannot send a request to a blocked user");
        case PENDING -> throw new BadRequestException("Friend request already pending");
        case FRIEND -> throw new BadRequestException("You are already friends");
        case REJECTED -> {
          f.setSender(sender);
          f.setReceiver(receiver);
          f.setStatus(FriendshipStatus.PENDING);
          friendshipRepository.save(f);
          // Bắn event thông báo request được gửi lại
          eventPublisher.publishEvent(FriendRequestEvent.friendRequest(userId, targetId));
          return new FriendshipResponse(
              "Friend request re-sent", FriendshipStatus.PENDING, userId, targetId);
        }
      }
    }

    friendshipRepository.save(
        Friendship.builder()
            .sender(sender)
            .receiver(receiver)
            .status(FriendshipStatus.PENDING)
            .build());

    // Bắn event để Notification-Service tạo thông báo (qua Kafka)
    eventPublisher.publishEvent(FriendRequestEvent.friendRequest(userId, targetId));

    return new FriendshipResponse(
        "Friend request sent", FriendshipStatus.PENDING, userId, targetId);
  }

  @Override
  @Transactional
  public FriendshipResponse acceptRequest(Long senderId, Long receiverId) {
    Friendship f = getFriendship(senderId, receiverId);
    if (f.getStatus() != FriendshipStatus.PENDING)
      throw new BadRequestException("Cannot approve a request that does not have status PENDING");
    if (blockUtils.isBlocked(senderId, receiverId) || blockUtils.isBlocked(receiverId, senderId))
      throw new BadRequestException("Cannot send request — one of you has blocked the other");

    f.setStatus(FriendshipStatus.FRIEND);
    friendshipRepository.save(f);

    User sender = getUser(senderId);
    User receiver = getUser(receiverId);

    // Auto follow hai chiều
    if (!userRelaRepository.existsByFollowerAndFollowing(sender, receiver))
      userRelaRepository.save(UserRela.builder().follower(sender).following(receiver).build());
    if (!userRelaRepository.existsByFollowerAndFollowing(receiver, sender))
      userRelaRepository.save(UserRela.builder().follower(receiver).following(sender).build());

    // Bắn Event để Message-Service tạo Conversation & Notification-Service tạo thông báo
    eventPublisher.publishEvent(new FriendshipAcceptedEvent(senderId, receiverId));

    return new FriendshipResponse(
        "Friend request accepted", FriendshipStatus.FRIEND, senderId, receiverId);
  }

  @Override
  @Transactional
  public FriendshipResponse rejectRequest(Long senderId, Long receiverId) {
    Friendship f = getFriendship(senderId, receiverId);
    if (f.getStatus() != FriendshipStatus.PENDING)
      throw new BadRequestException("Cannot reject a request that does not have status PENDING");
    friendshipRepository.delete(f);
    return new FriendshipResponse(
        "Friend request rejected", FriendshipStatus.REJECTED, senderId, receiverId);
  }

  @Override
  @Transactional
  public FriendshipResponse unfriend(Long userId, Long friendId) {
    if (userId.equals(friendId)) throw new BadRequestException("You cannot unfriend yourself");
    User u1 = getUser(userId);
    User u2 = getUser(friendId);

    Optional<Friendship> f1 = friendshipRepository.findBySenderAndReceiver(u1, u2);
    Optional<Friendship> f2 = friendshipRepository.findBySenderAndReceiver(u2, u1);
    if (f1.isEmpty() && f2.isEmpty())
      throw new ResourceNotFoundException("Not friends with this user");

    f1.ifPresent(friendshipRepository::delete);
    f2.ifPresent(friendshipRepository::delete);
    userRelaRepository.deleteByFollowerAndFollowing(u1, u2);
    userRelaRepository.deleteByFollowerAndFollowing(u2, u1);

    eventPublisher.publishEvent(new FriendshipDeletedEvent(userId, friendId));
    return new FriendshipResponse(
        "Unfriended successfully", FriendshipStatus.REJECTED, userId, friendId);
  }

  @Override
  @Transactional
  public FriendshipResponse blockUser(Long userId, Long targetId) {
    if (userId.equals(targetId)) throw new BadRequestException("You cannot block yourself");
    User user = getUser(userId);
    User target = getUser(targetId);

    friendshipRepository
        .findBySenderAndReceiver(user, target)
        .ifPresent(friendshipRepository::delete);
    friendshipRepository
        .findBySenderAndReceiver(target, user)
        .ifPresent(friendshipRepository::delete);
    userRelaRepository.deleteByFollowerAndFollowing(user, target);
    userRelaRepository.deleteByFollowerAndFollowing(target, user);

    Friendship f =
        friendshipRepository
            .findBySenderAndReceiver(user, target)
            .orElse(Friendship.builder().sender(user).receiver(target).build());
    f.setStatus(FriendshipStatus.BLOCKED);
    friendshipRepository.save(f);

    eventPublisher.publishEvent(new FriendshipDeletedEvent(userId, targetId));
    return new FriendshipResponse(
        "User blocked successfully", FriendshipStatus.BLOCKED, userId, targetId);
  }

  @Override
  @Transactional
  public FriendshipResponse unblockUser(Long userId, Long targetId) {
    Friendship friendship =
        friendshipRepository
            .findBySenderAndReceiver(getUser(userId), getUser(targetId))
            .orElseThrow(() -> new ResourceNotFoundException("No blocked relationship found"));
    if (friendship.getStatus() != FriendshipStatus.BLOCKED)
      throw new BadRequestException("This user is not blocked");
    friendshipRepository.delete(friendship);
    return new FriendshipResponse(
        "User unblocked successfully", FriendshipStatus.REJECTED, userId, targetId);
  }

  @Override
  @Transactional
  public FriendshipResponse unsendRequest(Long userId, Long targetId) {
    Friendship f =
        friendshipRepository
            .findBySenderAndReceiver(getUser(userId), getUser(targetId))
            .or(
                () ->
                    friendshipRepository.findBySenderAndReceiver(
                        getUser(targetId), getUser(userId)))
            .orElseThrow(() -> new ResourceNotFoundException("No friend request found"));

    if (!f.getSender().getId().equals(userId))
      throw new AccessDeniedException("You cannot unsend a request you didn’t send");
    if (f.getStatus() != FriendshipStatus.PENDING)
      throw new BadRequestException("Cannot unsend a request that is not pending");

    friendshipRepository.delete(f);
    return new FriendshipResponse("Friend request unsent", null, userId, targetId);
  }

  // =================================================================================
  // GETTERS & PAGINATION (BULK FETCH N+1 FIX)
  // =================================================================================

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFriends(Long userId, String filter, Pageable pageable) {
    User user = getUser(userId);
    Specification<Friendship> spec =
        (root, q, cb) ->
            cb.and(
                cb.equal(root.get("status"), FriendshipStatus.FRIEND),
                cb.or(cb.equal(root.get("sender"), user), cb.equal(root.get("receiver"), user)));
    // Note: Nếu bạn có custom filter, hãy nối Specification filter vào `spec` ở đây.
    Page<Friendship> page = friendshipRepository.findAll(spec, pageable);
    return mapToPageVO(
        userId, page, f -> f.getSender().equals(user) ? f.getReceiver() : f.getSender());
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getPendingRequests(Long userId, String filter, Pageable pageable) {
    User user = getUser(userId);
    Specification<Friendship> spec =
        (root, q, cb) ->
            cb.and(
                cb.equal(root.get("receiver"), user),
                cb.equal(root.get("status"), FriendshipStatus.PENDING));
    Page<Friendship> page = friendshipRepository.findAll(spec, pageable);
    return mapToPageVO(userId, page, Friendship::getSender);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getSentRequests(Long userId, String filter, Pageable pageable) {
    User user = getUser(userId);
    Specification<Friendship> spec =
        (root, q, cb) ->
            cb.and(
                cb.equal(root.get("sender"), user),
                cb.equal(root.get("status"), FriendshipStatus.PENDING));
    Page<Friendship> page = friendshipRepository.findAll(spec, pageable);
    return mapToPageVO(userId, page, Friendship::getReceiver);
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getBlockedUsers(Long userId, String filter, Pageable pageable) {
    User user = getUser(userId);
    Specification<Friendship> spec =
        (root, q, cb) ->
            cb.and(
                cb.equal(root.get("sender"), user),
                cb.equal(root.get("status"), FriendshipStatus.BLOCKED));
    Page<Friendship> page = friendshipRepository.findAll(spec, pageable);
    return mapToPageVO(userId, page, Friendship::getReceiver);
  }

  // =================================================================================
  // HELPERS
  // =================================================================================

  private User getUser(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  private Friendship getFriendship(Long userId1, Long userId2) {
    User u1 = getUser(userId1);
    User u2 = getUser(userId2);
    return friendshipRepository
        .findBySenderAndReceiver(u1, u2)
        .or(() -> friendshipRepository.findBySenderAndReceiver(u2, u1))
        .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));
  }

  private PageVO<UserRelationDto> mapToPageVO(
      Long viewerId, Page<Friendship> page, Function<Friendship, User> targetExtractor) {
    List<User> targets = page.getContent().stream().map(targetExtractor).toList();
    if (targets.isEmpty()) return buildEmptyPageVO(page);

    List<Long> targetIds = targets.stream().map(User::getId).toList();

    // 1. Bulk Fetch (Chỉ 3 câu Query cho toàn bộ Page)
    Set<Long> myFollowingIds =
        userRelaRepository.findFollowingIdsByViewerAndTargets(viewerId, targetIds);
    Set<Long> myFollowerIds =
        userRelaRepository.findFollowerIdsByViewerAndTargets(viewerId, targetIds);
    List<Friendship> bulkFriendships =
        friendshipRepository.findFriendshipsBetween(viewerId, targetIds);

    Map<Long, FriendshipResponse> friendshipMap =
        bulkFriendships.stream()
            .collect(
                Collectors.toMap(
                    f ->
                        f.getSender().getId().equals(viewerId)
                            ? f.getReceiver().getId()
                            : f.getSender().getId(),
                    f -> FriendshipResponse.from(f, viewerId),
                    (existing, replacement) -> existing));

    // 2. Map sang DTO
    List<UserRelationDto> content =
        targets.stream()
            .map(
                target -> {
                  var userInfo = target.getUserInfo();
                  String bio = (userInfo != null) ? userInfo.getBio() : null;
                  String favorites = (userInfo != null) ? userInfo.getFavorites() : null;
                  var dateOfBirth = (userInfo != null) ? userInfo.getDateOfBirth() : null;

                  UserRelationDto dto =
                      UserRelationDto.builder()
                          .id(target.getId())
                          .displayName(target.getDisplayName())
                          .avatarUrl(target.getAvatarUrl())
                          .bio(bio)
                          .favorites(favorites)
                          .dateOfBirth(dateOfBirth)
                          .isFollowing(myFollowingIds.contains(target.getId()))
                          .isFollowedBy(myFollowerIds.contains(target.getId()))
                          .friendship(
                              friendshipMap.getOrDefault(
                                  target.getId(), FriendshipResponse.builder().build()))
                          .build();

                  return dto;
                })
            .collect(Collectors.toList());

    return PageVO.<UserRelationDto>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  private PageVO<UserRelationDto> buildEmptyPageVO(Page<?> page) {
    return PageVO.<UserRelationDto>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(0)
        .content(List.of())
        .build();
  }

  // ==========================================
  // INTERNAL APIs (For other microservices)
  // ==========================================

  @Override
  @Transactional(readOnly = true)
  public List<Long> getNetworkIds(Long userId) {
    // Kiểm tra user tồn tại
    User user = getUser(userId);

    // Lấy danh sách bạn bè (trạng thái FRIEND)
    Set<Long> friendIds = new HashSet<>();
    List<Friendship> friendships =
        friendshipRepository.findAll(
            (root, query, cb) -> {
              Predicate isFriend = cb.equal(root.get("status"), FriendshipStatus.FRIEND);
              Predicate isInvolved =
                  cb.or(cb.equal(root.get("sender"), user), cb.equal(root.get("receiver"), user));
              return cb.and(isFriend, isInvolved);
            });

    for (Friendship f : friendships) {
      if (f.getSender().getId().equals(userId)) {
        friendIds.add(f.getReceiver().getId());
      } else {
        friendIds.add(f.getSender().getId());
      }
    }

    // Lấy danh sách những người user đang follow (sử dụng UserRela)
    Set<Long> followingIds =
        userRelaRepository.findByFollower(user).stream()
            .map(r -> r.getFollowing().getId())
            .collect(Collectors.toSet());

    // Merge cả hai set (bạn bè + những người follow)
    Set<Long> networkIds = new HashSet<>(friendIds);
    networkIds.addAll(followingIds);

    return new ArrayList<>(networkIds);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isFriend(Long user1, Long user2) {
    if (user1.equals(user2)) return true;

    return friendshipRepository.existsActiveFriendship(user1, user2);
  }
}
