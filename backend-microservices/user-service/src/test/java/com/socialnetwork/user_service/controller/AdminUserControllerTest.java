package com.socialnetwork.user_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.socialnetwork.user_service.dto.AdminUserViewDto;
import com.socialnetwork.common.vo.AccountStatus;
import com.socialnetwork.user_service.service.Neo4jMigrationService;
import com.socialnetwork.user_service.service.UserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.socialnetwork.common.security.JwtAuthenticationFilter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.socialnetwork.common.vo.PageVO;

/**
 * Tests for {@link AdminUserController} – specifically the API Composition logic in {@code
 * getAllUsersForAdmin}.
 *
 * <p>The user-service calls {@link com.socialnetwork.user_service.client.AuthClient} (HTTP
 * Interface/Feign-like) to fetch credential data (email, roles, status) from {@code auth-service}
 * and merges it with local {@code User} data. In these tests, {@code UserService} is mocked with
 * {@code @MockBean}, so the AuthClient stub is internal to {@code UserServiceImpl}. We control the
 * merged output via the mocked service.
 */
@WebMvcTest(
    controllers = AdminUserController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    },
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
class AdminUserControllerTest {

  private static final String ADMIN_USERS_URL = "/api/v1/users/admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserService userService;
  @MockitoBean private Neo4jMigrationService neo4jMigrationService;

  // ─────────────────────────────────────────────────────────────────
  //  Helpers – build test fixtures
  // ─────────────────────────────────────────────────────────────────

  /**
   * Builds an {@link AdminUserViewDto} that simulates the result of the API Composition in {@code
   * UserServiceImpl.getAllUsersForAdmin()}. The dto already merges local profile fields
   * (displayName, avatarUrl, bio) with remote auth fields (email, username, status, roles).
   */
  private AdminUserViewDto buildComposedDto(
      Long id,
      String displayName,
      String email,
      String username,
      AccountStatus status,
      Set<String> roles) {
    AdminUserViewDto dto = new AdminUserViewDto();
    dto.setId(id);
    dto.setDisplayName(displayName);
    dto.setAvatarUrl("https://cdn.example.com/avatars/" + id + ".png");
    // -- From auth-service (simulates AuthClient response merged in) --
    dto.setCredentialId(id + 1000L);
    dto.setEmail(email);
    dto.setUsername(username);
    dto.setStatus(status);
    dto.setRoles(roles);
    // -- From local DB --
    dto.setBio("Bio for " + displayName);
    return dto;
  }

  private PageVO<AdminUserViewDto> pageOf(List<AdminUserViewDto> content) {
    return PageVO.<AdminUserViewDto>builder()
        .page(0)
        .size(10)
        .totalElements((long) content.size())
        .totalPages(1)
        .numberOfElements(content.size())
        .content(content)
        .build();
  }

  // ─────────────────────────────────────────────────────────────────
  //  Tests
  // ─────────────────────────────────────────────────────────────────

  @Test
  @WithMockUser(authorities = {"ROLE_ADMIN", "USER:READ_ALL", "USER:UPDATE_ANY"})
  @DisplayName("GET /admin (no filter) → 200 OK with composed user list (email + roles merged)")
  void givenNoFilter_whenGetAllUsers_thenReturnsComposedList() throws Exception {
    // --- Arrange ---
    AdminUserViewDto user1 =
        buildComposedDto(
            1L, "Alice", "alice@sn.com", "alice", AccountStatus.ACTIVE, Set.of("USER"));
    AdminUserViewDto user2 =
        buildComposedDto(
            2L, "Bob", "bob@sn.com", "bob", AccountStatus.ACTIVE, Set.of("USER", "MODERATOR"));

    when(userService.getAllUsersForAdmin(isNull(), any(Pageable.class)))
        .thenReturn(pageOf(List.of(user1, user2)));

    // --- Act ---
    MvcResult result =
        mockMvc
            .perform(get(ADMIN_USERS_URL).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andReturn();

    // --- Assert: verify the composition merged email from auth-service ---
    String body = result.getResponse().getContentAsString();
    PageVO<AdminUserViewDto> page =
        objectMapper.readValue(body, new TypeReference<PageVO<AdminUserViewDto>>() {});

    AdminUserViewDto alice = page.getContent().get(0);
    assertThat(alice.getId()).isEqualTo(1L);
    assertThat(alice.getDisplayName()).isEqualTo("Alice");
    // These fields come from the mocked AuthClient response
    assertThat(alice.getEmail()).isEqualTo("alice@sn.com");
    assertThat(alice.getUsername()).isEqualTo("alice");
    assertThat(alice.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(alice.getRoles()).contains("USER");
    // This field comes from local user DB
    assertThat(alice.getBio()).isEqualTo("Bio for Alice");

    AdminUserViewDto bob = page.getContent().get(1);
    assertThat(bob.getEmail()).isEqualTo("bob@sn.com");
    assertThat(bob.getRoles()).containsExactlyInAnyOrder("USER", "MODERATOR");
  }

  @Test
  @WithMockUser(authorities = {"ROLE_ADMIN", "USER:READ_ALL", "USER:UPDATE_ANY"})
  @DisplayName(
      "GET /admin?filter=… → filter is forwarded to service and results are paged correctly")
  void givenRsqlFilter_whenGetAllUsers_thenFilterForwardedToService() throws Exception {
    // --- Arrange ---
    String rsqlFilter = "displayName==Alice";
    AdminUserViewDto alice =
        buildComposedDto(
            1L, "Alice", "alice@sn.com", "alice", AccountStatus.ACTIVE, Set.of("USER"));

    when(userService.getAllUsersForAdmin(eq(rsqlFilter), any(Pageable.class)))
        .thenReturn(pageOf(List.of(alice)));

    // --- Act & Assert ---
    mockMvc
        .perform(
            get(ADMIN_USERS_URL).param("filter", rsqlFilter).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].displayName").value("Alice"));

    verify(userService).getAllUsersForAdmin(eq(rsqlFilter), any(Pageable.class));
  }

  @Test
  @WithMockUser(authorities = {"ROLE_ADMIN", "USER:READ_ALL", "USER:UPDATE_ANY"})
  @DisplayName("GET /admin → empty page when no users exist → returns 200 with empty content array")
  void givenNoUsers_whenGetAllUsers_thenReturnsEmptyPage() throws Exception {
    // --- Arrange ---
    when(userService.getAllUsersForAdmin(any(), any(Pageable.class))).thenReturn(pageOf(List.of()));

    // --- Act & Assert ---
    mockMvc
        .perform(get(ADMIN_USERS_URL).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @WithMockUser(authorities = {"ROLE_ADMIN", "USER:READ_ALL", "USER:UPDATE_ANY"})
  @DisplayName(
      "GET /admin → AuthClient data absent for one user → null email/roles in composed DTO")
  void givenAuthClientReturnsPartialData_whenGetAllUsers_thenNullFieldsAreAccepted()
      throws Exception {
    // Simulate a user whose auth-service record is missing (null authInfo in toAdminViewDto)
    AdminUserViewDto orphanUser = new AdminUserViewDto();
    orphanUser.setId(99L);
    orphanUser.setDisplayName("Orphan User");
    // email, username, roles, status left null – mimics the null-safe guard in toAdminViewDto

    when(userService.getAllUsersForAdmin(any(), any(Pageable.class)))
        .thenReturn(pageOf(List.of(orphanUser)));

    mockMvc
        .perform(get(ADMIN_USERS_URL).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(99))
        .andExpect(jsonPath("$.content[0].displayName").value("Orphan User"))
        .andExpect(jsonPath("$.content[0].email").doesNotExist());
  }

  @Test
  @WithMockUser(authorities = {"ROLE_ADMIN", "USER:READ_ALL", "USER:UPDATE_ANY"})
  @DisplayName("GET /admin?page=0&size=5 → pagination parameters forwarded to service")
  void givenPaginationParams_whenGetAllUsers_thenPageableForwardedCorrectly() throws Exception {
    // --- Arrange ---
    when(userService.getAllUsersForAdmin(any(), any(Pageable.class))).thenReturn(pageOf(List.of()));

    // --- Act ---
    mockMvc
        .perform(
            get(ADMIN_USERS_URL)
                .param("page", "0")
                .param("size", "5")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // Verify that a Pageable with size 5 was passed through
    verify(userService)
        .getAllUsersForAdmin(
            isNull(), argThat(p -> p.getPageSize() == 5 && p.getPageNumber() == 0));
  }

  @Test
  @WithMockUser(authorities = {"ROLE_ADMIN", "USER:READ_ALL", "USER:UPDATE_ANY"})
  @DisplayName("GET /admin → user with BLOCKED status is correctly returned")
  void givenBlockedUser_whenGetAllUsers_thenStatusIsPreserved() throws Exception {
    // --- Arrange ---
    AdminUserViewDto blockedUser =
        buildComposedDto(
            10L,
            "BlockedUser",
            "blocked@sn.com",
            "blocked01",
            AccountStatus.BLOCKED,
            Set.of("USER"));

    when(userService.getAllUsersForAdmin(any(), any(Pageable.class)))
        .thenReturn(pageOf(List.of(blockedUser)));

    // --- Act & Assert ---
    mockMvc
        .perform(get(ADMIN_USERS_URL).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("BLOCKED"));
  }
}
