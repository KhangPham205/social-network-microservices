package com.socialnetwork.user_service.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.user_service.event.EventPublisher;
import com.socialnetwork.user_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Registration saga: a redelivered event must answer again, never fail and never duplicate. */
@ExtendWith(MockitoExtension.class)
class UserSagaHandlerTest {

  private static final Long ACCOUNT_ID = 7L;
  private static final UserCreatedEvent EVENT =
      new UserCreatedEvent(ACCOUNT_ID, "alice", "alice@sn.com");
  private static final UserSummary SUMMARY = new UserSummary(ACCOUNT_ID, "alice", null);

  @Mock private UserService userService;
  @Mock private EventPublisher eventPublisher;

  @InjectMocks private UserSagaHandler handler;

  @Test
  @DisplayName("first delivery: the profile is created and the saga is answered")
  void givenNewAccount_whenEventHandled_thenProfileCreatedIsPublished() {
    when(userService.createDefaultProfile(ACCOUNT_ID, "alice")).thenReturn(SUMMARY);

    handler.handleUserCreatedEvent(EVENT);

    verify(userService).createDefaultProfile(ACCOUNT_ID, "alice");
    verify(eventPublisher).publishProfileCreated(ACCOUNT_ID);
    verify(eventPublisher, never()).publishProfileFailed(anyLong(), any());
  }

  @Test
  @DisplayName("redelivery: an existing profile is kept and the reply is published again")
  void givenExistingProfile_whenEventRedelivered_thenReplyIsRepublished() {
    // The service is idempotent: it returns the existing profile instead of failing.
    when(userService.createDefaultProfile(ACCOUNT_ID, "alice")).thenReturn(SUMMARY);

    handler.handleUserCreatedEvent(EVENT);
    handler.handleUserCreatedEvent(EVENT);

    verify(userService, times(2)).createDefaultProfile(ACCOUNT_ID, "alice");
    verify(eventPublisher, times(2)).publishProfileCreated(ACCOUNT_ID);
    verify(eventPublisher, never()).publishProfileFailed(anyLong(), any());
  }

  @Test
  @DisplayName("failure: auth-service is told to compensate")
  void givenServiceFailure_whenEventHandled_thenProfileFailedIsPublished() {
    when(userService.createDefaultProfile(ACCOUNT_ID, "alice"))
        .thenThrow(new IllegalStateException("db down"));

    handler.handleUserCreatedEvent(EVENT);

    verify(eventPublisher).publishProfileFailed(eq(ACCOUNT_ID), anyString());
    verify(eventPublisher, never()).publishProfileCreated(anyLong());
  }
}
