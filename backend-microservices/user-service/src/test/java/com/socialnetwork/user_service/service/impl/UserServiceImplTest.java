package com.socialnetwork.user_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.user_service.client.AuthClient;
import com.socialnetwork.user_service.event.EventPublisher;
import com.socialnetwork.user_service.model.User;
import com.socialnetwork.user_service.repository.FriendshipRepository;
import com.socialnetwork.user_service.repository.UserRelaRepository;
import com.socialnetwork.user_service.repository.UserRepository;
import com.socialnetwork.user_service.utils.BlockUtils;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Profile creation is driven by a Kafka event, so it has to tolerate redeliveries. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  private static final Long ACCOUNT_ID = 7L;

  @Mock private UserRepository userRepository;
  @Mock private UserRelaRepository userRelaRepository;
  @Mock private FriendshipRepository friendshipRepository;
  @Mock private AuthClient authClient;
  @Mock private BlockUtils blockUtils;
  @Mock private EventPublisher eventPublisher;

  @InjectMocks private UserServiceImpl userService;

  @Test
  @DisplayName("createDefaultProfile: a new account gets a profile and a ProfileUpdatedEvent")
  void givenNewAccount_whenCreateDefaultProfile_thenProfileIsStored() {
    when(userRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

    UserSummary summary = userService.createDefaultProfile(ACCOUNT_ID, "alice");

    assertThat(summary.id()).isEqualTo(ACCOUNT_ID);
    assertThat(summary.displayName()).isEqualTo("alice");
    verify(userRepository).save(any(User.class));
    verify(eventPublisher).publishProfileUpdated(ACCOUNT_ID, "alice", null);
  }

  @Test
  @DisplayName("createDefaultProfile: an existing profile is returned untouched")
  void givenExistingProfile_whenCreateDefaultProfile_thenNothingIsOverwritten() {
    User existing = User.builder().id(ACCOUNT_ID).displayName("Alice Renamed").build();
    when(userRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(existing));

    UserSummary summary = userService.createDefaultProfile(ACCOUNT_ID, "alice");

    assertThat(summary.displayName()).isEqualTo("Alice Renamed");
    verify(userRepository, never()).save(any(User.class));
    verify(eventPublisher, never()).publishProfileUpdated(anyLong(), anyString(), any());
  }
}
