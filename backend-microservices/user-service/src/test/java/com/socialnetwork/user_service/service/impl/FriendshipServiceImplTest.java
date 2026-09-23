package com.socialnetwork.user_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.vo.FriendshipStatus;
import com.socialnetwork.user_service.dto.FriendshipResponse;
import com.socialnetwork.user_service.event.EventPublisher;
import com.socialnetwork.user_service.model.Friendship;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRelaRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.utils.BlockUtils;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Rules that the HTTP layer cannot enforce: who may answer a request, and what a block touches. */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceImplTest {

  private static final Long ALICE = 1L;
  private static final Long BOB = 2L;

  @Mock private FriendshipRepository friendshipRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserRelaRepository userRelaRepository;
  @Mock private BlockUtils blockUtils;
  @Mock private EventPublisher eventPublisher;

  @InjectMocks private FriendshipServiceImpl friendshipService;

  private User alice;
  private User bob;

  @BeforeEach
  void setUp() {
    alice = user(ALICE, "Alice");
    bob = user(BOB, "Bob");
  }

  private static User user(Long id, String name) {
    return User.builder().id(id).displayName(name).build();
  }

  private static Friendship friendship(User sender, User receiver, FriendshipStatus status) {
    return Friendship.builder().sender(sender).receiver(receiver).status(status).build();
  }

  private void givenUsers() {
    when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
    when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
  }

  // ------------------------------------------------------------------ accept

  @Test
  @DisplayName("accept: the receiver of the request turns it into a friendship")
  void givenPendingRequest_whenReceiverAccepts_thenBecomesFriends() {
    givenUsers();
    Friendship pending = friendship(alice, bob, FriendshipStatus.PENDING);
    when(friendshipRepository.findBySenderAndReceiver(alice, bob)).thenReturn(Optional.of(pending));
    when(blockUtils.isBlockedEitherWay(ALICE, BOB)).thenReturn(false);

    FriendshipResponse response = friendshipService.acceptRequest(ALICE, BOB);

    assertThat(response.getStatus()).isEqualTo(FriendshipStatus.FRIEND);
    assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.FRIEND);
    verify(friendshipRepository).save(pending);
    verify(eventPublisher).publishFriendAccepted(ALICE, BOB);
  }

  @Test
  @DisplayName("accept: the sender of the request cannot accept it himself")
  void givenPendingRequest_whenSenderAccepts_thenAccessDenied() {
    givenUsers();
    // Alice sent the request to Bob; Alice must not be able to accept her own request.
    Friendship pending = friendship(alice, bob, FriendshipStatus.PENDING);
    when(friendshipRepository.findBySenderAndReceiver(bob, alice)).thenReturn(Optional.empty());
    when(friendshipRepository.findBySenderAndReceiver(alice, bob)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> friendshipService.acceptRequest(BOB, ALICE))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(pending.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    verify(friendshipRepository, never()).save(any());
    verify(eventPublisher, never()).publishFriendAccepted(anyLong(), anyLong());
  }

  @Test
  @DisplayName("reject: only the receiver may reject the request")
  void givenPendingRequest_whenSenderRejects_thenAccessDenied() {
    givenUsers();
    Friendship pending = friendship(alice, bob, FriendshipStatus.PENDING);
    when(friendshipRepository.findBySenderAndReceiver(bob, alice)).thenReturn(Optional.empty());
    when(friendshipRepository.findBySenderAndReceiver(alice, bob)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> friendshipService.rejectRequest(BOB, ALICE))
        .isInstanceOf(AccessDeniedException.class);

    verify(friendshipRepository, never()).delete(any(Friendship.class));
  }

  // ------------------------------------------------------------------ block

  @Test
  @DisplayName("block: directional, the block the other side holds survives")
  void givenMutualBlocks_whenBlocking_thenOnlyOwnRowIsWritten() {
    givenUsers();
    Friendship bobBlockedAlice = friendship(bob, alice, FriendshipStatus.BLOCKED);
    when(friendshipRepository.findBySenderAndReceiver(alice, bob)).thenReturn(Optional.empty());
    when(friendshipRepository.findBySenderAndReceiver(bob, alice))
        .thenReturn(Optional.of(bobBlockedAlice));

    FriendshipResponse response = friendshipService.blockUser(ALICE, BOB);

    assertThat(response.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);

    ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
    verify(friendshipRepository).save(saved.capture());
    assertThat(saved.getValue().getSender()).isEqualTo(alice);
    assertThat(saved.getValue().getReceiver()).isEqualTo(bob);
    assertThat(saved.getValue().getStatus()).isEqualTo(FriendshipStatus.BLOCKED);

    // Bob's own block is untouched, and the follows go both ways.
    verify(friendshipRepository, never()).delete(bobBlockedAlice);
    verify(userRelaRepository).deleteFollowsBetween(ALICE, BOB);
    verify(eventPublisher).publishFriendshipDeleted(ALICE, BOB);
  }

  @Test
  @DisplayName("block: a pending request from the other side is dropped")
  void givenPendingFromTarget_whenBlocking_thenThatRowIsDeleted() {
    givenUsers();
    Friendship bobsRequest = friendship(bob, alice, FriendshipStatus.PENDING);
    when(friendshipRepository.findBySenderAndReceiver(alice, bob)).thenReturn(Optional.empty());
    when(friendshipRepository.findBySenderAndReceiver(bob, alice))
        .thenReturn(Optional.of(bobsRequest));

    friendshipService.blockUser(ALICE, BOB);

    verify(friendshipRepository).delete(bobsRequest);
  }

  // ------------------------------------------------------------------ unfriend

  @Test
  @DisplayName("unfriend: deletes the FRIEND rows of the pair and nothing else")
  void givenFriends_whenUnfriend_thenOnlyFriendRowsAreDeleted() {
    Friendship friendRow = friendship(alice, bob, FriendshipStatus.FRIEND);
    when(friendshipRepository.findFriendRowsBetween(ALICE, BOB)).thenReturn(List.of(friendRow));

    FriendshipResponse response = friendshipService.unfriend(ALICE, BOB);

    assertThat(response.getSenderId()).isEqualTo(ALICE);
    verify(friendshipRepository).deleteAll(List.of(friendRow));
    verify(friendshipRepository, never()).delete(any(Friendship.class));
    verify(userRelaRepository).deleteFollowsBetween(ALICE, BOB);
    verify(eventPublisher).publishFriendshipDeleted(ALICE, BOB);
  }

  @Test
  @DisplayName("unfriend: a block between the two is not a friendship")
  void givenOnlyBlockRow_whenUnfriend_thenNotFound() {
    when(friendshipRepository.findFriendRowsBetween(ALICE, BOB)).thenReturn(List.of());

    assertThatThrownBy(() -> friendshipService.unfriend(ALICE, BOB))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(friendshipRepository, never()).deleteAll(any());
    verify(userRelaRepository, never()).deleteFollowsBetween(anyLong(), anyLong());
  }

  // ------------------------------------------------------------------ unblock

  @Test
  @DisplayName("unblock: removes the caller's own block row only")
  void givenOwnBlock_whenUnblock_thenOnlyThatRowIsDeleted() {
    givenUsers();
    Friendship aliceBlockedBob = friendship(alice, bob, FriendshipStatus.BLOCKED);
    when(friendshipRepository.findBySenderAndReceiverAndStatus(
            alice, bob, FriendshipStatus.BLOCKED))
        .thenReturn(Optional.of(aliceBlockedBob));

    friendshipService.unblockUser(ALICE, BOB);

    verify(friendshipRepository).delete(aliceBlockedBob);
  }
}
