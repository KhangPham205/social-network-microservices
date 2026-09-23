package com.socialnetwork.chat_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.socialnetwork.chat_service.client.UserClient;
import com.socialnetwork.chat_service.config.CacheConfig;
import com.socialnetwork.chat_service.service.impl.CachedUserDirectory;
import com.socialnetwork.common.dto.UserSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@ExtendWith(MockitoExtension.class)
class CachedUserDirectoryTest {

  @Mock private UserClient userClient;

  private CachedUserDirectory directory;

  @BeforeEach
  void setUp() {
    directory =
        new CachedUserDirectory(
            userClient, new ConcurrentMapCacheManager(CacheConfig.USER_SUMMARIES));
  }

  @Test
  void everyParticipantIsResolvedWithASingleCall() {
    when(userClient.getSummaries(anyList()))
        .thenReturn(List.of(summary(1L), summary(2L), summary(3L)));

    Map<Long, UserSummary> resolved = directory.getSummaries(List.of(1L, 2L, 3L, 2L));

    assertThat(resolved).containsOnlyKeys(1L, 2L, 3L);
    verify(userClient, times(1)).getSummaries(List.of(1L, 2L, 3L));
    verifyNoMoreInteractions(userClient);
  }

  @Test
  void aSecondLookupIsServedFromTheCache() {
    when(userClient.getSummaries(List.of(1L))).thenReturn(List.of(summary(1L)));

    directory.getSummaries(List.of(1L));
    directory.getSummaries(List.of(1L));

    verify(userClient, times(1)).getSummaries(List.of(1L));
  }

  @Test
  void onlyTheCacheMissesAreFetched() {
    when(userClient.getSummaries(List.of(1L))).thenReturn(List.of(summary(1L)));
    when(userClient.getSummaries(List.of(2L))).thenReturn(List.of(summary(2L)));

    directory.getSummaries(List.of(1L));
    directory.getSummaries(List.of(1L, 2L));

    verify(userClient).getSummaries(List.of(1L));
    verify(userClient).getSummaries(List.of(2L));
    verifyNoMoreInteractions(userClient);
  }

  @Test
  void anUnknownUserFallsBackToAPlaceholderInsteadOfBreakingTheListing() {
    when(userClient.getSummaries(List.of(42L))).thenReturn(List.of());

    UserSummary summary = directory.getSummary(42L);

    assertThat(summary.id()).isEqualTo(42L);
    assertThat(summary.displayName()).isNotBlank();
  }

  private static UserSummary summary(Long id) {
    return new UserSummary(id, "User " + id, "https://cdn/" + id + ".png");
  }
}
