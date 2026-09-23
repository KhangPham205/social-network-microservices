package com.socialnetwork.auth_service.compensator;

import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.ProfileCreatedEvent;
import com.socialnetwork.common.events.ProfileFailedEvent;
import com.socialnetwork.common.vo.AccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replies of the registration saga. Both handlers are idempotent: Kafka may redeliver a record, and
 * a second delivery must not undo a state the user has already moved past (e.g. an account that has
 * meanwhile verified its e-mail and become {@code ACTIVE}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthSagaCompensator {

  private final UserCredentialRepository userCredentialRepository;

  /** Profile created: {@code WAITING -> PENDING}, leaving any later status untouched. */
  @KafkaListener(topics = KafkaTopics.PROFILE_CREATED)
  @Transactional
  public void handleProfileCreated(ProfileCreatedEvent event) {
    UserCredential credential = find(event.accountId());
    if (credential == null) {
      return;
    }
    if (credential.getStatus() != AccountStatus.WAITING) {
      log.debug(
          "Ignoring ProfileCreatedEvent for account {}: status is already {}",
          event.accountId(),
          credential.getStatus());
      return;
    }
    credential.setStatus(AccountStatus.PENDING);
    userCredentialRepository.save(credential);
    log.info("Profile created for account {}; awaiting e-mail verification", event.accountId());
  }

  /**
   * Profile creation failed: the credential is kept for support and marked {@code NOT_SOLVED}, never
   * hard-deleted.
   */
  @KafkaListener(topics = KafkaTopics.PROFILE_FAILED)
  @Transactional
  public void handleProfileFailed(ProfileFailedEvent event) {
    UserCredential credential = find(event.accountId());
    if (credential == null) {
      return;
    }
    if (credential.getStatus() == AccountStatus.NOT_SOLVED) {
      log.debug("Ignoring duplicate ProfileFailedEvent for account {}", event.accountId());
      return;
    }
    credential.setStatus(AccountStatus.NOT_SOLVED);
    userCredentialRepository.save(credential);
    log.warn(
        "Profile creation failed for account {} ({}); marked NOT_SOLVED",
        event.accountId(),
        event.reason());
  }

  private UserCredential find(Long accountId) {
    if (accountId == null) {
      throw new IllegalArgumentException("Saga reply without accountId");
    }
    UserCredential credential = userCredentialRepository.findById(accountId).orElse(null);
    if (credential == null) {
      log.warn("Saga reply for unknown account {}; nothing to compensate", accountId);
    }
    return credential;
  }
}
