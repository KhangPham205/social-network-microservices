package com.socialnetwork.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalTokenAuthenticationFilterTest {

  private final InternalTokenAuthenticationFilter filter =
      new InternalTokenAuthenticationFilter("s2s-secret");

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void matchingHeaderGrantsInternalRole() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users/internal/1");
    req.addHeader("X-Internal-Token", "s2s-secret");
    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityUtils.hasRole("INTERNAL")).isTrue();
    assertThat(SecurityUtils.findCurrentUserId()).isEmpty();
  }

  @Test
  void wrongOrMissingHeaderStaysAnonymous() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users/internal/1");
    req.addHeader("X-Internal-Token", "nope");
    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
