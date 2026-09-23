package com.socialnetwork.notification_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialnetwork.common.config.CommonSecurityAutoConfiguration;
import com.socialnetwork.common.constants.ApiConstants;
import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.GlobalExceptionHandler;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.notification_service.config.SecurityConfig;
import com.socialnetwork.notification_service.dto.ActorDto;
import com.socialnetwork.notification_service.dto.NotificationCountDto;
import com.socialnetwork.notification_service.dto.NotificationDto;
import com.socialnetwork.notification_service.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Web-layer contract of {@link NotificationController}: the endpoints are private, ownership is
 * enforced in the service layer and surfaces as 403, and the read flag is published as {@code
 * isRead}.
 */
@WebMvcTest(controllers = NotificationController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({SecurityConfig.class, CommonSecurityAutoConfiguration.class, GlobalExceptionHandler.class})
@TestPropertySource(
    properties = {
      "jwt.secret=test-only-jwt-secret-0123456789abcdef0123456789abcdef",
      "app.internal.token=test-internal-token"
    })
class NotificationControllerTest {

  private static final Long VIEWER_ID = 42L;
  private static final String BASE = ApiConstants.NOTIFICATIONS;

  @Autowired private WebApplicationContext context;

  @MockitoBean private NotificationService notificationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static NotificationDto dto(boolean read) {
    return new NotificationDto(
        11L,
        new ActorDto(1L, "Actor", "a.png"),
        "content",
        "/posts/3",
        read,
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  @DisplayName("an anonymous caller gets 401 on the notification list")
  void anonymousListIsUnauthorized() throws Exception {
    mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    verify(notificationService, never()).getMyNotifications(any(), any());
  }

  @Test
  @DisplayName("an anonymous caller gets 401 on the unread counter")
  void anonymousUnreadCountIsUnauthorized() throws Exception {
    mockMvc.perform(get(BASE + "/unread-count")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("an anonymous caller gets 401 when marking a notification as read")
  void anonymousMarkAsReadIsUnauthorized() throws Exception {
    mockMvc.perform(put(BASE + "/9/read")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("the viewer only ever reads their own notifications; the JSON flag is isRead")
  void listReturnsOwnNotifications() throws Exception {
    when(notificationService.getMyNotifications(eq(VIEWER_ID), any(Pageable.class)))
        .thenReturn(
            PageVO.<NotificationDto>builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .numberOfElements(1)
                .content(List.of(dto(true)))
                .build());

    mockMvc
        .perform(get(BASE).with(user(VIEWER_ID.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].isRead").value(true))
        .andExpect(jsonPath("$.content[0].read").doesNotExist())
        .andExpect(jsonPath("$.content[0].actor.id").value(1));

    verify(notificationService).getMyNotifications(eq(VIEWER_ID), any(Pageable.class));
  }

  @Test
  @DisplayName("the unread counter is scoped to the caller")
  void unreadCountIsScopedToCaller() throws Exception {
    when(notificationService.getUnreadCount(VIEWER_ID)).thenReturn(new NotificationCountDto(5L));

    mockMvc
        .perform(get(BASE + "/unread-count").with(user(VIEWER_ID.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(5));
  }

  @Test
  @DisplayName("reading someone else's notification is answered with 403")
  void readingForeignNotificationIsForbidden() throws Exception {
    when(notificationService.markAsRead(VIEWER_ID, 9L))
        .thenThrow(new AccessDeniedException("Notification does not belong to the current user"));

    mockMvc
        .perform(put(BASE + "/9/read").with(user(VIEWER_ID.toString())))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("reading an own notification returns the updated payload")
  void readingOwnNotificationSucceeds() throws Exception {
    when(notificationService.markAsRead(VIEWER_ID, 11L)).thenReturn(dto(true));

    mockMvc
        .perform(put(BASE + "/11/read").with(user(VIEWER_ID.toString())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(11))
        .andExpect(jsonPath("$.isRead").value(true));
  }
}
