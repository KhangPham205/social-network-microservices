package com.socialnetwork.moderation_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialnetwork.common.config.CommonSecurityAutoConfiguration;
import com.socialnetwork.common.vo.AccountStatus;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.config.SecurityConfig;
import com.socialnetwork.moderation_service.service.ModerationService;
import com.socialnetwork.moderation_service.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Proves the fix for the critical gap: {@code @EnableMethodSecurity} is now on, so the
 * {@code @PreAuthorize} rules of the block endpoints are actually enforced. A plain user is
 * rejected with 403, an administrator gets through.
 */
@WebMvcTest(
    controllers = ModerationController.class,
    excludeAutoConfiguration = {
      DataSourceAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class
    })
@ImportAutoConfiguration(CommonSecurityAutoConfiguration.class)
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "jwt.secret=test-only-jwt-secret-0123456789abcdef0123456789",
      "app.internal.token=test-internal-token"
    })
class ModerationControllerSecurityTest {

  private static final String BLOCK_POST_URL = "/api/v1/moderation/POST/42/block";
  private static final String UNBLOCK_POST_URL = "/api/v1/moderation/POST/42/unblock";
  private static final String BLOCK_USER_URL = "/api/v1/moderation/users/9/block";

  @Autowired private WebApplicationContext context;

  @MockitoBean private ModerationService moderationService;
  @MockitoBean private ReportService reportService;

  private MockMvc mockMvc;

  /**
   * Spring Boot 4 no longer applies the Spring Security MockMvc configurer automatically, so the
   * test security context would never reach the filter chain. Wire it explicitly.
   */
  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  // ── Plain user ───────────────────────────────────────────────────────────

  @Test
  @WithMockUser(username = "2", roles = "USER")
  @DisplayName("A plain USER gets 403 when blocking content")
  void plainUserCannotBlockContent() throws Exception {
    mockMvc.perform(put(BLOCK_POST_URL)).andExpect(status().isForbidden());
    verify(moderationService, never()).blockContent(any(), any(), any());
  }

  @Test
  @WithMockUser(username = "2", roles = "USER")
  @DisplayName("A plain USER gets 403 when restoring content")
  void plainUserCannotUnblockContent() throws Exception {
    mockMvc.perform(put(UNBLOCK_POST_URL)).andExpect(status().isForbidden());
    verify(moderationService, never()).unblockContent(any(), any(), any());
  }

  @Test
  @WithMockUser(username = "2", roles = "USER")
  @DisplayName("A plain USER gets 403 when blocking an account")
  void plainUserCannotBlockAnAccount() throws Exception {
    mockMvc.perform(put(BLOCK_USER_URL)).andExpect(status().isForbidden());
    verify(moderationService, never()).updateUserStatus(any(), any(), any());
  }

  @Test
  @WithAnonymousUser
  @DisplayName("An anonymous caller gets 401")
  void anonymousIsUnauthorized() throws Exception {
    mockMvc.perform(put(BLOCK_POST_URL)).andExpect(status().isUnauthorized());
    verify(moderationService, never()).blockContent(any(), any(), any());
  }

  // ── Administrator ────────────────────────────────────────────────────────

  @Test
  @WithMockUser(username = "1", roles = "ADMIN")
  @DisplayName("An ADMIN may block content")
  void adminCanBlockContent() throws Exception {
    mockMvc.perform(put(BLOCK_POST_URL)).andExpect(status().isOk());
    verify(moderationService).blockContent(eq("42"), eq(TargetType.POST), isNull());
  }

  @Test
  @WithMockUser(username = "1", roles = "ADMIN")
  @DisplayName("An ADMIN may restore content")
  void adminCanUnblockContent() throws Exception {
    mockMvc.perform(put(UNBLOCK_POST_URL)).andExpect(status().isOk());
    verify(moderationService).unblockContent(eq("42"), eq(TargetType.POST), isNull());
  }

  @Test
  @WithMockUser(username = "1", roles = "ADMIN")
  @DisplayName("An ADMIN may block an account")
  void adminCanBlockAnAccount() throws Exception {
    mockMvc.perform(put(BLOCK_USER_URL)).andExpect(status().isOk());
    verify(moderationService).updateUserStatus(eq(9L), eq(AccountStatus.BLOCKED), isNull());
  }

  // ── Fine-grained permission instead of the role ──────────────────────────

  @Test
  @WithMockUser(username = "3", authorities = "MODERATION:ACCESS")
  @DisplayName("The MODERATION:ACCESS permission alone is enough to block content")
  void moderationPermissionIsEnough() throws Exception {
    mockMvc.perform(put(BLOCK_POST_URL)).andExpect(status().isOk());
    verify(moderationService).blockContent(eq("42"), eq(TargetType.POST), isNull());
  }
}
