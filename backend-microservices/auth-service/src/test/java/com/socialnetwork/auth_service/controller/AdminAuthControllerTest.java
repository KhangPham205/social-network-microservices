package com.socialnetwork.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialnetwork.auth_service.config.SecurityConfig;
import com.socialnetwork.auth_service.dto.CreateStaffRequest;
import com.socialnetwork.auth_service.dto.RegisterResponse;
import com.socialnetwork.auth_service.service.AuthService;
import com.socialnetwork.common.config.CommonSecurityAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Authorization of {@code /api/v1/auth/admin/**}, exercised through the real {@link SecurityConfig}.
 *
 * <p>{@link CommonSecurityAutoConfiguration} is imported explicitly because the {@code @WebMvcTest}
 * slice does not apply auto-configurations that are not registered for it, and {@code SecurityConfig}
 * needs the shared {@code JwtSecurityConfigurer} it declares.
 */
@WebMvcTest(controllers = AdminAuthController.class)
@Import({SecurityConfig.class, CommonSecurityAutoConfiguration.class})
@ActiveProfiles("test")
class AdminAuthControllerTest {

  private static final String CREATE_STAFF_URL = "/api/v1/auth/admin/staff";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthService authService;

  private static CreateStaffRequest validRequest() {
    CreateStaffRequest request = new CreateStaffRequest();
    request.setUsername("moderator_01");
    request.setPassword("Secr3tP@ss!");
    request.setEmail("mod01@socialnetwork.com");
    request.setFullname("Moderator One");
    request.setRoleName("MODERATOR");
    return request;
  }

  private ResultActions createStaff() throws Exception {
    return createStaff(validRequest());
  }

  /**
   * {@code testSecurityContext()} bridges {@code @WithMockUser} into the request: Boot 4 no longer
   * applies Spring Security's MockMvc configurer to the {@code @WebMvcTest} slice, so without it
   * {@code SecurityContextHolderFilter} would clear the context and every request would be anonymous.
   */
  private ResultActions createStaff(CreateStaffRequest request) throws Exception {
    return mockMvc.perform(
        post(CREATE_STAFF_URL)
            .with(testSecurityContext())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
  }

  @Test
  @WithAnonymousUser
  @DisplayName("anonymous -> 401 and the service is never reached")
  void anonymousIsUnauthorized() throws Exception {
    createStaff().andExpect(status().isUnauthorized());

    verify(authService, never()).createStaffAccount(any());
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("an authenticated non-admin -> 403 and the service is never reached")
  void regularUserIsForbidden() throws Exception {
    createStaff().andExpect(status().isForbidden());

    verify(authService, never()).createStaffAccount(any());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("ROLE_ADMIN -> 200 with the service response")
  void adminCanCreateStaff() throws Exception {
    when(authService.createStaffAccount(any(CreateStaffRequest.class)))
        .thenReturn(new RegisterResponse("Staff account created successfully"));

    createStaff()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Staff account created successfully"));

    verify(authService)
        .createStaffAccount(
            org.mockito.ArgumentMatchers.argThat(
                r -> "moderator_01".equals(r.getUsername()) && "MODERATOR".equals(r.getRoleName())));
  }

  @Test
  @WithMockUser(authorities = "USER:CREATE")
  @DisplayName("a bare permission without ROLE_ADMIN is not enough -> 403")
  void permissionWithoutAdminRoleIsForbidden() throws Exception {
    createStaff().andExpect(status().isForbidden());

    verify(authService, never()).createStaffAccount(any());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("an invalid body is rejected with 400 before the service is called")
  void invalidBodyIsRejected() throws Exception {
    CreateStaffRequest request = validRequest();
    request.setEmail("not-an-email");

    createStaff(request).andExpect(status().isBadRequest());

    verify(authService, never()).createStaffAccount(any());
  }
}
