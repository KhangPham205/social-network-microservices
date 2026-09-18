package com.socialnetwork.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** Request details plus the raw token, so services can propagate it to downstream calls. */
@Getter
public class JwtAuthenticationDetails extends WebAuthenticationDetails {

  private final String token;
  private final String username;

  public JwtAuthenticationDetails(HttpServletRequest request, String token, String username) {
    super(request);
    this.token = token;
    this.username = username;
  }
}
