package com.socialnetwork.media_service.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.socialnetwork.common.events.ModerationActionEvent;
import com.socialnetwork.common.vo.ModerationAction;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.service.CommentService;
import com.socialnetwork.media_service.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Routing rules of the moderation listener, without a broker. */
@ExtendWith(MockitoExtension.class)
class ModerationActionEventListenerTest {

  @Mock private PostService postService;
  @Mock private CommentService commentService;

  @InjectMocks private ModerationActionEventListener listener;

  private static ModerationActionEvent event(
      String targetId, TargetType type, ModerationAction action) {
    return ModerationActionEvent.builder()
        .targetId(targetId)
        .targetType(type)
        .action(action)
        .build();
  }

  @Test
  @DisplayName("BLOCK on a POST bans it")
  void blockPost() {
    listener.onModerationAction(event("7", TargetType.POST, ModerationAction.BLOCK));

    verify(postService).updateSystemBanStatus(7L, true);
    verifyNoInteractions(commentService);
  }

  @Test
  @DisplayName("UNBLOCK on a COMMENT lifts its ban")
  void unblockComment() {
    listener.onModerationAction(event("8", TargetType.COMMENT, ModerationAction.UNBLOCK));

    verify(commentService).updateSystemBanStatus(8L, false);
    verifyNoInteractions(postService);
  }

  @Test
  @DisplayName("target types owned by other services are ignored")
  void unsupportedTargetTypeIsIgnored() {
    listener.onModerationAction(event("9", TargetType.MESSAGE, ModerationAction.BLOCK));

    verifyNoInteractions(postService, commentService);
  }

  @Test
  @DisplayName("an incomplete event is rejected as non-retryable")
  void incompleteEventIsRejected() {
    assertThatThrownBy(() -> listener.onModerationAction(event("9", null, ModerationAction.BLOCK)))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(postService, commentService);
  }

  @Test
  @DisplayName("a non numeric target id is rejected as non-retryable")
  void nonNumericIdIsRejected() {
    assertThatThrownBy(
            () ->
                listener.onModerationAction(
                    event("abc", TargetType.POST, ModerationAction.BLOCK)))
        .isInstanceOf(IllegalArgumentException.class);

    verify(postService, org.mockito.Mockito.never()).updateSystemBanStatus(anyLong(), anyBoolean());
  }
}
