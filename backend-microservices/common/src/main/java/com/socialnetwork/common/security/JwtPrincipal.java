package com.socialnetwork.common.security;

import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/** Identity extracted from a verified access token. */
public record JwtPrincipal(Long userId, String username, List<GrantedAuthority> authorities) {}
