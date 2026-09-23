package com.socialnetwork.auth_service.listener;

import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.UserModerationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * moderation-service decisions about an account. The new status is applied to the credential and,
 * when it is no longer {@code ACTIVE} (typically {@code BLOCKED}), every refresh token of the user
 * is revoked so an open session cannot be extended.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserModerationEventListener {

  private final AuthService authService;

  @KafkaListener(topics = KafkaTopics.USER_MODERATION_ACTIONS)
  public void onUserModerated(UserModerationEvent event) {
    if (event.userId() == null || event.newStatus() == null) {
      throw new IllegalArgumentException("UserModerationEvent without userId or newStatus");
    }
    log.info(
        "Moderation sets account {} to {} ({})",
        event.userId(),
        event.newStatus(),
        event.reason());
    authService.updateRoleAndStatus(event.userId(), null, event.newStatus());
  }
}
