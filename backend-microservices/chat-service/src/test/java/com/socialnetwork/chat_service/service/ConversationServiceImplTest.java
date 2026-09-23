package com.socialnetwork.chat_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.chat_service.dto.AddMembersRequest;
import com.socialnetwork.chat_service.dto.ConversationCreateRequest;
import com.socialnetwork.chat_service.dto.MarkReadRequest;
import com.socialnetwork.chat_service.dto.UpdateConversationRequest;
import com.socialnetwork.chat_service.dto.UpdateMemberRoleRequest;
import com.socialnetwork.chat_service.enums.ConversationRole;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.model.RoomMember;
import com.socialnetwork.chat_service.model.RoomMemberId;
import com.socialnetwork.chat_service.repository.jpa.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import com.socialnetwork.chat_service.repository.mongo.ChatMessageRepository;
import com.socialnetwork.chat_service.service.impl.ChatRoomWriter;
import com.socialnetwork.chat_service.service.impl.ConversationServiceImpl;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceImplTest {

  private static final Long ROOM_ID = 10L;
  private static final Long OWNER_ID = 1L;
  private static final Long ADMIN_ID = 2L;
  private static final Long MEMBER_ID = 3L;
  private static final Long OTHER_ADMIN_ID = 4L;
  private static final Long OUTSIDER_ID = 99L;

  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private RoomMemberRepository roomMemberRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatRoomWriter chatRoomWriter;
  @Mock private UserDirectory userDirectory;
  @Mock private SimpMessagingTemplate messagingTemplate;

  @InjectMocks private ConversationServiceImpl conversationService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ------------------------------------------------------------ role rules

  @Test
  void plainMemberCannotUpdateTheGroup() {
    givenMember(MEMBER_ID, ConversationRole.MEMBER, true);
    UpdateConversationRequest request = new UpdateConversationRequest();
    request.setConversationId(ROOM_ID);
    request.setTitle("New title");

    assertThatThrownBy(() -> conversationService.updateConversation(MEMBER_ID, request))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void plainMemberCannotAddMembers() {
    givenMember(MEMBER_ID, ConversationRole.MEMBER, true);
    AddMembersRequest request = new AddMembersRequest();
    request.setConversationId(ROOM_ID);
    request.setUserIds(List.of(50L));

    assertThatThrownBy(() -> conversationService.addMembersToGroup(MEMBER_ID, request))
        .isInstanceOf(AccessDeniedException.class);
    verify(roomMemberRepository, never()).saveAll(any());
  }

  @Test
  void groupActionsAreRejectedOnPrivateRooms() {
    givenMember(OWNER_ID, ConversationRole.OWNER, false);
    AddMembersRequest request = new AddMembersRequest();
    request.setConversationId(ROOM_ID);
    request.setUserIds(List.of(50L));

    assertThatThrownBy(() -> conversationService.addMembersToGroup(OWNER_ID, request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void theOwnerCannotBeRemoved() {
    givenMember(ADMIN_ID, ConversationRole.ADMIN, true);
    givenTargetMember(OWNER_ID, ConversationRole.OWNER);

    assertThatThrownBy(
            () -> conversationService.removeMemberFromGroup(ADMIN_ID, ROOM_ID, OWNER_ID))
        .isInstanceOf(AccessDeniedException.class);
    verify(roomMemberRepository, never()).delete(any());
  }

  @Test
  void anAdminCannotRemoveAnotherAdmin() {
    givenMember(ADMIN_ID, ConversationRole.ADMIN, true);
    givenTargetMember(OTHER_ADMIN_ID, ConversationRole.ADMIN);

    assertThatThrownBy(
            () -> conversationService.removeMemberFromGroup(ADMIN_ID, ROOM_ID, OTHER_ADMIN_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void removingYourselfMustUseTheLeaveEndpoint() {
    givenMember(ADMIN_ID, ConversationRole.ADMIN, true);

    assertThatThrownBy(
            () -> conversationService.removeMemberFromGroup(ADMIN_ID, ROOM_ID, ADMIN_ID))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void ownerRoleCannotBeGrantedThroughTheRoleEndpoint() {
    givenMember(OWNER_ID, ConversationRole.OWNER, true);
    givenTargetMember(MEMBER_ID, ConversationRole.MEMBER);

    UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
    request.setConversationId(ROOM_ID);
    request.setUserIdToChange(MEMBER_ID);
    request.setNewRole(ConversationRole.OWNER);

    assertThatThrownBy(() -> conversationService.updateMemberRole(OWNER_ID, request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void ownerWithOtherMembersMustTransferOwnershipBeforeLeaving() {
    givenMember(OWNER_ID, ConversationRole.OWNER, true);
    when(roomMemberRepository.countByIdRoomId(ROOM_ID)).thenReturn(3L);

    assertThatThrownBy(() -> conversationService.leaveConversation(OWNER_ID, ROOM_ID))
        .isInstanceOf(BadRequestException.class);
    verify(chatRoomRepository, never()).delete(any());
  }

  @Test
  void aNonMemberIsNotAllowedToLeave() {
    when(roomMemberRepository.findByIdRoomIdAndIdUserId(ROOM_ID, OUTSIDER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.leaveConversation(OUTSIDER_ID, ROOM_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  // -------------------------------------------------------------- reading

  @Test
  void aNonMemberCannotReadTheConversation() {
    when(chatRoomRepository.findById(ROOM_ID))
        .thenReturn(Optional.of(ChatRoom.builder().id(ROOM_ID).isGroup(true).active(true).build()));
    when(roomMemberRepository.findByIdRoomIdAndIdUserId(ROOM_ID, OUTSIDER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.getConversationById(OUTSIDER_ID, ROOM_ID))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void anUnknownConversationIsNotFound() {
    when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.getConversationById(OWNER_ID, ROOM_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ------------------------------------------------------------ read state

  @Test
  void aNonMemberCannotMarkAMessageAsRead() {
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, OUTSIDER_ID)).thenReturn(false);

    assertThatThrownBy(() -> conversationService.markMessageAsRead(OUTSIDER_ID, markRead("m1")))
        .isInstanceOf(AccessDeniedException.class);
    verify(chatMessageRepository, never()).save(any());
  }

  @Test
  void aMessageFromAnotherConversationCannotBeMarkedAsRead() {
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);
    when(chatMessageRepository.findById("m1"))
        .thenReturn(
            Optional.of(
                ChatMessage.builder().id("m1").roomId(555L).createdAt(Instant.now()).build()));

    assertThatThrownBy(() -> conversationService.markMessageAsRead(MEMBER_ID, markRead("m1")))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(chatMessageRepository, never()).save(any());
  }

  @Test
  void markingAMessageAsReadStoresTheReaderAndNotifiesTheRoom() {
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);
    ChatMessage message =
        ChatMessage.builder().id("m1").roomId(ROOM_ID).createdAt(Instant.now()).build();
    when(chatMessageRepository.findById("m1")).thenReturn(Optional.of(message));

    conversationService.markMessageAsRead(MEMBER_ID, markRead("m1"));

    assertThat(message.getReadBy()).containsExactly(MEMBER_ID);
    verify(chatMessageRepository).save(message);
    verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
  }

  // ------------------------------------------------------------- creation

  @Test
  void aPrivateConversationNeedsExactlyOneOtherMember() {
    authenticateAs(OWNER_ID);
    ConversationCreateRequest request =
        ConversationCreateRequest.builder().isGroup(false).memberIds(List.of(2L, 3L)).build();

    assertThatThrownBy(() -> conversationService.createConversation(request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void aGroupConversationNeedsATitle() {
    authenticateAs(OWNER_ID);
    ConversationCreateRequest request =
        ConversationCreateRequest.builder().isGroup(true).memberIds(List.of(2L, 3L)).build();

    assertThatThrownBy(() -> conversationService.createConversation(request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void anExistingPrivateRoomIsReusedInsteadOfCreatingADuplicate() {
    authenticateAs(OWNER_ID);
    ChatRoom existing =
        ChatRoom.builder()
            .id(ROOM_ID)
            .isGroup(false)
            .active(true)
            .pairKey(ChatRoom.pairKeyOf(OWNER_ID, MEMBER_ID))
            .build();
    when(chatRoomRepository.findByPairKey(ChatRoom.pairKeyOf(OWNER_ID, MEMBER_ID)))
        .thenReturn(Optional.of(existing));
    when(chatRoomWriter.reactivate(existing)).thenReturn(existing);
    when(roomMemberRepository.findUserIdsByRoomId(ROOM_ID))
        .thenReturn(List.of(OWNER_ID, MEMBER_ID));

    ConversationCreateRequest request =
        ConversationCreateRequest.builder().isGroup(false).memberIds(List.of(MEMBER_ID)).build();

    assertThat(conversationService.createConversation(request).getId()).isEqualTo(ROOM_ID);
    verify(chatRoomWriter, never()).createPrivateRoom(anyLong(), anyLong(), any());
  }

  @Test
  void archivingAnUnknownPairIsANoOp() {
    when(chatRoomRepository.findByPairKey(anyString())).thenReturn(Optional.empty());

    conversationService.archiveConversationForFriends(OWNER_ID, MEMBER_ID);

    verify(chatRoomWriter, never()).archive(any());
  }

  @Test
  void archivingClosesTheRoomAndNotifiesIt() {
    ChatRoom room = ChatRoom.builder().id(ROOM_ID).isGroup(false).active(true).build();
    when(chatRoomRepository.findByPairKey(ChatRoom.pairKeyOf(OWNER_ID, MEMBER_ID)))
        .thenReturn(Optional.of(room));

    conversationService.archiveConversationForFriends(OWNER_ID, MEMBER_ID);

    verify(chatRoomWriter).archive(room);
    verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
  }

  // -------------------------------------------------------------- helpers

  private void givenMember(Long userId, ConversationRole role, boolean group) {
    ChatRoom room = ChatRoom.builder().id(ROOM_ID).isGroup(group).active(true).build();
    RoomMember member =
        RoomMember.builder()
            .id(new RoomMemberId(ROOM_ID, userId))
            .chatRoom(room)
            .role(role)
            .joinedAt(Instant.now())
            .build();
    when(roomMemberRepository.findByIdRoomIdAndIdUserId(ROOM_ID, userId))
        .thenReturn(Optional.of(member));
    when(userDirectory.getSummary(anyLong()))
        .thenAnswer(invocation -> new UserSummary(invocation.getArgument(0), "User", null));
    when(userDirectory.getSummaries(any()))
        .thenAnswer(
            invocation -> {
              Iterable<Long> ids = invocation.getArgument(0);
              java.util.Map<Long, UserSummary> map = new java.util.LinkedHashMap<>();
              ids.forEach(id -> map.put(id, new UserSummary(id, "User " + id, null)));
              return Map.copyOf(map);
            });
  }

  private void givenTargetMember(Long userId, ConversationRole role) {
    ChatRoom room = ChatRoom.builder().id(ROOM_ID).isGroup(true).active(true).build();
    when(roomMemberRepository.findByIdRoomIdAndIdUserId(ROOM_ID, userId))
        .thenReturn(
            Optional.of(
                RoomMember.builder()
                    .id(new RoomMemberId(ROOM_ID, userId))
                    .chatRoom(room)
                    .role(role)
                    .joinedAt(Instant.now())
                    .build()));
  }

  private static MarkReadRequest markRead(String messageId) {
    MarkReadRequest request = new MarkReadRequest();
    request.setConversationId(ROOM_ID);
    request.setMessageId(messageId);
    return request;
  }

  private static void authenticateAs(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of()));
  }
}
