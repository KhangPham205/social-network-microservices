package com.socialnetwork.auth_service.compensator;

import com.socialnetwork.auth_service.enums.AccountStatus;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import events.ProfileCreatedEvent;
import events.ProfileFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSagaCompensator {

  private final UserCredentialRepository userCredentialRepository;

  @KafkaListener(topics = "profile-created-topic", groupId = "auth-service-group-v2")
  public void handleProfileCreated(ProfileCreatedEvent event) {
    log.info("Profile created successfully. Activating accountId: {}", event.accountId());

    userCredentialRepository
        .findById(event.accountId())
        .ifPresent(
            user -> {
              user.setStatus(AccountStatus.PENDING); // Update WAITING -> PENDING
              userCredentialRepository.save(user);
            });
  }

  @KafkaListener(topics = "profile-failed-topic", groupId = "auth-service-group-v2")
  public void handleProfileFailed(ProfileFailedEvent event) {
    log.error(
        "Profile creation failed for accountId: {}. Reason: {}. Executing Compensation...",
        event.accountId(),
        event.reason());

    // Xóa luôn User bị lỗi để data sạch sẽ
    userCredentialRepository.deleteById(event.accountId());

    // Cập nhật thành FAILED (nếu bạn muốn lưu vết)
    // userCredentialRepository.findById(event.accountId()).ifPresent(user -> {
    //     user.setStatus(AccountStatus.FAILED);
    //     userCredentialRepository.save(user);
    // });

    log.info("Compensation completed. User deleted/marked as failed.");
  }
}
