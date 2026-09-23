package com.socialnetwork.notification_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.dto.UserSummary;
import com.socialnetwork.common.events.ProfileUpdatedEvent;
import com.socialnetwork.common.events.UserCreatedEvent;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.notification_service.client.UserServiceClient;
import com.socialnetwork.notification_service.model.UserCache;
import com.socialnetwork.notification_service.repository.UserCacheRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

/** Actor read-model rules of {@link UserCacheServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class UserCacheServiceImplTest {

  private static final Long USER_ID = 7L;

  @Mock private UserCacheRepository userCacheRepository;
  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private UserCacheServiceImpl service;

  @Test
  @DisplayName("a cached actor is returned without calling user-service")
  void getOrFetch_returnsCachedActor() {
    UserCache cached = UserCache.builder().id(USER_ID).displayName("Cached").build();
    when(userCacheRepository.findById(USER_ID)).thenReturn(Optional.of(cached));

    assertThat(service.getOrFetch(USER_ID)).isSameAs(cached);
    verifyNoInteractions(userServiceClient);
  }

  @Test
  @DisplayName("a cache miss fetches the summary from user-service and upserts it")
  void getOrFetch_fetchesAndCachesOnMiss() {
    when(userCacheRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userServiceClient.getSummaries(List.of(USER_ID)))
        .thenReturn(List.of(new UserSummary(USER_ID, "Fetched", "f.png")));
    when(userCacheRepository.save(any(UserCache.class))).thenAnswer(i -> i.getArgument(0));

    UserCache result = service.getOrFetch(USER_ID);

    assertThat(result.getId()).isEqualTo(USER_ID);
    assertThat(result.getDisplayName()).isEqualTo("Fetched");
    assertThat(result.getAvatarUrl()).isEqualTo("f.png");
    verify(userServiceClient).getSummaries(List.of(USER_ID));
    verify(userCacheRepository).save(any(UserCache.class));
  }

  @Test
  @DisplayName("user-service not knowing the id surfaces as ResourceNotFoundException")
  void getOrFetch_unknownUser() {
    when(userCacheRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userServiceClient.getSummaries(List.of(USER_ID))).thenReturn(List.of());

    assertThatThrownBy(() -> service.getOrFetch(USER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(userCacheRepository, never()).save(any());
  }

  @Test
  @DisplayName("a transport failure is never swallowed, so the record can be retried")
  void getOrFetch_propagatesTransportFailure() {
    when(userCacheRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userServiceClient.getSummaries(List.of(USER_ID)))
        .thenThrow(new ResourceAccessException("connection refused"));

    assertThatThrownBy(() -> service.getOrFetch(USER_ID))
        .isInstanceOf(ResourceAccessException.class);
    verify(userCacheRepository, never()).save(any());
  }

  @Test
  @DisplayName("UserCreatedEvent inserts a row and never overwrites an existing one")
  void onUserCreated_insertsOnce() {
    when(userCacheRepository.existsById(USER_ID)).thenReturn(false);
    when(userCacheRepository.save(any(UserCache.class))).thenAnswer(i -> i.getArgument(0));

    service.onUserCreated(new UserCreatedEvent(USER_ID, "newbie", "n@example.com"));

    ArgumentCaptor<UserCache> captor = ArgumentCaptor.forClass(UserCache.class);
    verify(userCacheRepository).save(captor.capture());
    assertThat(captor.getValue().getDisplayName()).isEqualTo("newbie");
  }

  @Test
  @DisplayName("UserCreatedEvent is idempotent")
  void onUserCreated_ignoresExisting() {
    when(userCacheRepository.existsById(USER_ID)).thenReturn(true);

    service.onUserCreated(new UserCreatedEvent(USER_ID, "newbie", "n@example.com"));

    verify(userCacheRepository, never()).save(any());
  }

  @Test
  @DisplayName("ProfileUpdatedEvent refreshes displayName and avatarUrl")
  void onProfileUpdated_refreshesIdentity() {
    UserCache cached =
        UserCache.builder().id(USER_ID).displayName("old").avatarUrl("old.png").build();
    when(userCacheRepository.findById(USER_ID)).thenReturn(Optional.of(cached));
    when(userCacheRepository.save(any(UserCache.class))).thenAnswer(i -> i.getArgument(0));

    service.onProfileUpdated(new ProfileUpdatedEvent(USER_ID, "new", "new.png"));

    assertThat(cached.getDisplayName()).isEqualTo("new");
    assertThat(cached.getAvatarUrl()).isEqualTo("new.png");
  }

  @Test
  @DisplayName("ProfileUpdatedEvent creates the row when the cache does not know the user yet")
  void onProfileUpdated_createsMissingRow() {
    when(userCacheRepository.findById(USER_ID)).thenReturn(Optional.empty());
    when(userCacheRepository.save(any(UserCache.class))).thenAnswer(i -> i.getArgument(0));

    service.onProfileUpdated(new ProfileUpdatedEvent(USER_ID, "new", null));

    ArgumentCaptor<UserCache> captor = ArgumentCaptor.forClass(UserCache.class);
    verify(userCacheRepository).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(USER_ID);
    assertThat(captor.getValue().getDisplayName()).isEqualTo("new");
  }

  @Test
  @DisplayName("events without an accountId are rejected as non-retryable")
  void nullAccountIdIsRejected() {
    assertThatThrownBy(() -> service.onUserCreated(new UserCreatedEvent(null, "x", "x@x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.onProfileUpdated(new ProfileUpdatedEvent(null, "x", null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
