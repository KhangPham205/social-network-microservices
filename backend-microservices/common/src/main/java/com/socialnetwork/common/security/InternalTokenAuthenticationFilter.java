package com.socialnetwork.common.security;

import static com.socialnetwork.common.constants.SecurityConstants.INTERNAL_PRINCIPAL;
import static com.socialnetwork.common.constants.SecurityConstants.INTERNAL_TOKEN_HEADER;
import static com.socialnetwork.common.constants.SecurityConstants.ROLE_INTERNAL;
import static com.socialnetwork.common.constants.SecurityConstants.ROLE_PREFIX;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates service-to-service calls: a request carrying the correct {@code X-Internal-Token}
 * header gets the {@code ROLE_INTERNAL} authority. Security rules then protect {@code /internal/**}
 * with {@code hasRole("INTERNAL")}. Runs before {@link JwtAuthenticationFilter}; a user JWT may
 * still be present and is ignored when the internal token matches.
 */
public class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

  private final byte[] expected;

  public InternalTokenAuthenticationFilter(String token) {
    this.expected = token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(INTERNAL_TOKEN_HEADER);
    if (header != null
        && SecurityContextHolder.getContext().getAuthentication() == null
        && MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8))) {
      var auth =
          new UsernamePasswordAuthenticationToken(
              INTERNAL_PRINCIPAL,
              null,
              List.of(new SimpleGrantedAuthority(ROLE_PREFIX + ROLE_INTERNAL)));
      SecurityContextHolder.getContext().setAuthentication(auth);
    }
    filterChain.doFilter(request, response);
  }
}
