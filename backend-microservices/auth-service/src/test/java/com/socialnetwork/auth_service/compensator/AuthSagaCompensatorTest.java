package com.socialnetwork.auth_service.compensator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.common.events.ProfileCreatedEvent;
import com.socialnetwork.common.events.ProfileFailedEvent;
import com.socialnetwork.common.vo.AccountStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Kafka may redeliver a saga reply; a second delivery must change nothing. */
@ExtendWith(MockitoExtension.class)
class AuthSagaCompensatorTest {

  @Mock private UserCredentialRepository userCredentialRepository;

  @InjectMocks private AuthSagaCompensator compensator;

  private static UserCredential credential(AccountStatus status) {
    return UserCredential.builder().id(7L).username("alice").status(status).build();
  }

  @Test
  @DisplayName("ProfileCreated moves WAITING to PENDING")
  void profileCreatedMovesWaitingToPending() {
    UserCredential user = credential(AccountStatus.WAITING);
    when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

    compensator.handleProfileCreated(new ProfileCreatedEvent(7L));

    assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING);
    verify(userCredentialRepository).save(user);
  }

  @ParameterizedTest
  @EnumSource(
      value = AccountStatus.class,
      names = {"PENDING", "ACTIVE", "BLOCKED", "NOT_AUTHORIZED", "NOT_SOLVED"})
  @DisplayName("a redelivered ProfileCreated never pulls a later status back to PENDING")
  void profileCreatedIsIdempotent(AccountStatus status) {
    UserCredential user = credential(status);
    when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

    compensator.handleProfileCreated(new ProfileCreatedEvent(7L));

    assertThat(user.getStatus()).isEqualTo(status);
    verify(userCredentialRepository, never()).save(any());
  }

  @Test
  @DisplayName("ProfileFailed marks the credential NOT_SOLVED and never deletes it")
  void profileFailedMarksNotSolvedWithoutDeleting() {
    UserCredential user = credential(AccountStatus.WAITING);
    when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

    compensator.handleProfileFailed(new ProfileFailedEvent(7L, "user-service down"));

    assertThat(user.getStatus()).isEqualTo(AccountStatus.NOT_SOLVED);
    verify(userCredentialRepository).save(user);
    verify(userCredentialRepository, never()).deleteById(anyLong());
    verify(userCredentialRepository, never()).delete(any());
  }

  @Test
  @DisplayName("a redelivered ProfileFailed writes nothing a second time")
  void profileFailedIsIdempotent() {
    UserCredential user = credential(AccountStatus.NOT_SOLVED);
    when(userCredentialRepository.findById(7L)).thenReturn(Optional.of(user));

    compensator.handleProfileFailed(new ProfileFailedEvent(7L, "user-service down"));

    verify(userCredentialRepository, never()).save(any());
    verify(userCredentialRepository, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("a saga reply for an unknown account is ignored instead of failing")
  void unknownAccountIsIgnored() {
    when(userCredentialRepository.findById(404L)).thenReturn(Optional.empty());

    compensator.handleProfileCreated(new ProfileCreatedEvent(404L));

    verify(userCredentialRepository, never()).save(any());
  }

  @Test
  @DisplayName("a malformed reply without accountId goes to the DLT, not into a retry loop")
  void replyWithoutAccountIdIsNotRetryable() {
    assertThatThrownBy(() -> compensator.handleProfileCreated(new ProfileCreatedEvent(null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
