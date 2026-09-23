package com.socialnetwork.chat_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.chat_service.dto.MediaItem;
import com.socialnetwork.chat_service.dto.MessageRequest;
import com.socialnetwork.chat_service.dto.MessageResponse;
import com.socialnetwork.chat_service.model.ChatMessage;
import com.socialnetwork.chat_service.model.ChatRoom;
import com.socialnetwork.chat_service.repository.jpa.ChatRoomRepository;
import com.socialnetwork.chat_service.repository.jpa.RoomMemberRepository;
import com.socialnetwork.chat_service.repository.mongo.ChatMessageRepository;
import com.socialnetwork.chat_service.service.impl.MessageServiceImpl;
import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.MessageCreatedEvent;
import com.socialnetwork.common.events.MessageNotificationEvent;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.vo.CursorPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceImplTest {

  private static final Long ROOM_ID = 10L;
  private static final Long MEMBER_ID = 7L;
  private static final Long OTHER_MEMBER_ID = 8L;
  private static final Long OUTSIDER_ID = 99L;

  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private RoomMemberRepository roomMemberRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private UserDirectory userDirectory;
  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  @InjectMocks private MessageServiceImpl messageService;

  @BeforeEach
  void setUp() {
    when(userDirectory.getSummary(anyLong()))
        .thenAnswer(invocation -> new UserSummary(invocation.getArgument(0), "User", null));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ------------------------------------------------------------ membership

  @Test
  void nonMemberCannotSendMessage() {
    givenRoom(true);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, OUTSIDER_ID)).thenReturn(false);

    assertThatThrownBy(() -> messageService.sendMessageAs(OUTSIDER_ID, textRequest()))
        .isInstanceOf(AccessDeniedException.class);

    verify(chatMessageRepository, never()).save(any());
    verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
  }

  @Test
  void nonMemberCannotReadMessages() {
    authenticateAs(OUTSIDER_ID);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, OUTSIDER_ID)).thenReturn(false);

    assertThatThrownBy(() -> messageService.getMessagesCursor(ROOM_ID, null, 30))
        .isInstanceOf(AccessDeniedException.class);

    verify(chatMessageRepository, never())
        .findByRoomIdOrderByCreatedAtDesc(anyLong(), any(PageRequest.class));
  }

  @Test
  void memberCanSendAndEveryoneElseIsNotified() {
    givenRoom(true);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);
    when(roomMemberRepository.findUserIdsByRoomId(ROOM_ID))
        .thenReturn(List.of(MEMBER_ID, OTHER_MEMBER_ID));
    when(chatMessageRepository.save(any(ChatMessage.class)))
        .thenAnswer(
            invocation -> {
              ChatMessage saved = invocation.getArgument(0);
              saved.setId("msg-1");
              return saved;
            });

    MessageResponse response = messageService.sendMessageAs(MEMBER_ID, textRequest());

    assertThat(response.getId()).isEqualTo("msg-1");
    assertThat(response.getConversationId()).isEqualTo(ROOM_ID);
    verify(messagingTemplate).convertAndSend(eq("/queue/conversation/10"), eq(response));
    verify(kafkaTemplate)
        .send(eq(KafkaTopics.MESSAGE_CREATED), eq("msg-1"), any(MessageCreatedEvent.class));
    verify(kafkaTemplate)
        .send(eq(KafkaTopics.CHAT_NOTIFICATION), eq("msg-1"), any(MessageNotificationEvent.class));
  }

  // ----------------------------------------------------------- validation

  @Test
  void unknownRoomIsNotFound() {
    when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> messageService.sendMessageAs(MEMBER_ID, textRequest()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void archivedRoomRejectsNewMessages() {
    givenRoom(false);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);

    assertThatThrownBy(() -> messageService.sendMessageAs(MEMBER_ID, textRequest()))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void messageWithoutContentOrMediaIsRejected() {
    MessageRequest empty = MessageRequest.builder().conversationId(ROOM_ID).content("  ").build();

    assertThatThrownBy(() -> messageService.sendMessageAs(MEMBER_ID, empty))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void messageWithOnlyMediaIsAccepted() {
    givenRoom(true);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);
    when(roomMemberRepository.findUserIdsByRoomId(ROOM_ID)).thenReturn(List.of(MEMBER_ID));
    when(chatMessageRepository.save(any(ChatMessage.class)))
        .thenAnswer(
            invocation -> {
              ChatMessage saved = invocation.getArgument(0);
              saved.setId("msg-2");
              return saved;
            });

    MessageRequest request =
        MessageRequest.builder()
            .conversationId(ROOM_ID)
            .media(List.of(new MediaItem("https://cdn/img.png", "image", "img.png")))
            .build();

    assertThat(messageService.sendMessageAs(MEMBER_ID, request).getMedia()).hasSize(1);
  }

  // -------------------------------------------------------- cursor paging

  @Test
  void cursorPageIsReturnedOldestFirstWithNextCursor() {
    authenticateAs(MEMBER_ID);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);
    when(chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(
            eq(ROOM_ID), any(PageRequest.class)))
        .thenReturn(
            new SliceImpl<>(List.of(message("c"), message("b")), PageRequest.of(0, 2), true));

    CursorPage<MessageResponse> page = messageService.getMessagesCursor(ROOM_ID, null, 2);

    assertThat(page.content()).extracting(MessageResponse::getId).containsExactly("b", "c");
    assertThat(page.nextCursor()).isEqualTo("b");
  }

  @Test
  void lastCursorPageHasNoNextCursor() {
    authenticateAs(MEMBER_ID);
    when(roomMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, MEMBER_ID)).thenReturn(true);
    when(chatMessageRepository.findByRoomIdAndIdLessThanOrderByCreatedAtDesc(
            eq(ROOM_ID), eq("b"), any(PageRequest.class)))
        .thenReturn(new SliceImpl<>(List.of(message("a")), PageRequest.of(0, 2), false));

    CursorPage<MessageResponse> page = messageService.getMessagesCursor(ROOM_ID, "b", 2);

    assertThat(page.content()).extracting(MessageResponse::getId).containsExactly("a");
    assertThat(page.nextCursor()).isNull();
  }

  // --------------------------------------------------------------- delete

  @Test
  void onlyTheAuthorCanDeleteAMessage() {
    authenticateAs(OUTSIDER_ID);
    when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message("msg-1")));

    assertThatThrownBy(() -> messageService.softDeleteMessage("msg-1"))
        .isInstanceOf(AccessDeniedException.class);
    verify(chatMessageRepository, never()).save(any());
  }

  // -------------------------------------------------------------- helpers

  private void givenRoom(boolean active) {
    ChatRoom room = ChatRoom.builder().id(ROOM_ID).isGroup(false).active(active).build();
    when(chatRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
  }

  private static MessageRequest textRequest() {
    return MessageRequest.builder().conversationId(ROOM_ID).content("hello").build();
  }

  private static ChatMessage message(String id) {
    return ChatMessage.builder()
        .id(id)
        .roomId(ROOM_ID)
        .senderId(MEMBER_ID)
        .content("hello")
        .createdAt(Instant.now())
        .isDeleted(false)
        .build();
  }

  private static void authenticateAs(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of()));
  }
}
