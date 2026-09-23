package com.socialnetwork.media_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialnetwork.common.exception.AccessDeniedException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.media_service.client.UserServiceClient;
import com.socialnetwork.media_service.enums.AccessScope;
import com.socialnetwork.media_service.model.Post;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.CommentRepository;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.service.impl.ContentAccessServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Visibility rules for a post: scope, ownership, moderation ban and soft delete. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentAccessServiceImplTest {

  private static final Long AUTHOR_ID = 1L;
  private static final Long VIEWER_ID = 2L;

  @Mock private PostRepository postRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private ContentAccessServiceImpl contentAccessService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static Post post(AccessScope scope) {
    return Post.builder()
        .id(10L)
        .author(UserCache.builder().id(AUTHOR_ID).displayName("author").build())
        .content("hello")
        .accessModifier(scope)
        .isSystemBan(false)
        .build();
  }

  private static void authenticateAs(Long userId, String... roles) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                "n/a",
                java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
  }

  @Nested
  @DisplayName("access scope")
  class Scope {

    @Test
    @DisplayName("PUBLIC posts are visible to anybody without asking user-service")
    void publicPostIsVisible() {
      assertThatCode(() -> contentAccessService.requireViewPermission(post(AccessScope.PUBLIC), VIEWER_ID))
          .doesNotThrowAnyException();
      verify(userServiceClient, never()).isFriend(anyLong(), anyLong());
    }

    @Test
    @DisplayName("FRIENDS posts are visible to friends")
    void friendsPostIsVisibleToFriend() {
      when(userServiceClient.isFriend(AUTHOR_ID, VIEWER_ID)).thenReturn(true);

      assertThatCode(() -> contentAccessService.requireViewPermission(post(AccessScope.FRIENDS), VIEWER_ID))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FRIENDS posts are denied to strangers")
    void friendsPostIsDeniedToStranger() {
      when(userServiceClient.isFriend(AUTHOR_ID, VIEWER_ID)).thenReturn(false);

      assertThatThrownBy(
              () -> contentAccessService.requireViewPermission(post(AccessScope.FRIENDS), VIEWER_ID))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("friends");
    }

    @Test
    @DisplayName("PRIVATE posts are denied to everybody but their author")
    void privatePostIsDeniedToOthers() {
      assertThatThrownBy(
              () -> contentAccessService.requireViewPermission(post(AccessScope.PRIVATE), VIEWER_ID))
          .isInstanceOf(AccessDeniedException.class);

      assertThatCode(() -> contentAccessService.requireViewPermission(post(AccessScope.PRIVATE), AUTHOR_ID))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("moderation and soft delete")
  class Hidden {

    @Test
    @DisplayName("a system-banned post is hidden from everybody but its author")
    void bannedPostIsHiddenFromNonAuthors() {
      Post banned = post(AccessScope.PUBLIC);
      banned.setIsSystemBan(true);

      assertThatThrownBy(() -> contentAccessService.requireViewPermission(banned, VIEWER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
      assertThat(contentAccessService.canView(banned, VIEWER_ID)).isFalse();

      assertThatCode(() -> contentAccessService.requireViewPermission(banned, AUTHOR_ID))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a soft deleted post is hidden from everybody but its author")
    void deletedPostIsHiddenFromNonAuthors() {
      Post deleted = post(AccessScope.PUBLIC);
      deleted.setDeletedAt(Instant.now());

      assertThatThrownBy(() -> contentAccessService.requireViewPermission(deleted, VIEWER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
      assertThat(contentAccessService.canView(deleted, AUTHOR_ID)).isTrue();
    }

    @Test
    @DisplayName("an administrator sees banned and private content")
    void adminSeesEverything() {
      authenticateAs(VIEWER_ID, "ROLE_ADMIN");
      Post banned = post(AccessScope.PRIVATE);
      banned.setIsSystemBan(true);

      assertThat(contentAccessService.canView(banned, VIEWER_ID)).isTrue();
    }
  }

  @Test
  @DisplayName("requireVisiblePost reports a missing post as not found")
  void missingPostIsNotFound() {
    when(postRepository.findById(99L)).thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> contentAccessService.requireVisiblePost(99L, VIEWER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(List.of(commentRepository)).isNotEmpty();
  }
}
