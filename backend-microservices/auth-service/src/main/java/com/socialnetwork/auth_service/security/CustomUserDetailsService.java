package com.socialnetwork.auth_service.security;

import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import com.socialnetwork.common.vo.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes credentials to Spring Security with the same authorities the JWT carries. */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserCredentialRepository userCredentialRepository;
  private final AuthorityMapper authorityMapper;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserCredential user =
        userCredentialRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    return User.withUsername(user.getUsername())
        .password(user.getPassword())
        .authorities(authorityMapper.grantedAuthorities(user))
        .accountLocked(user.getStatus() == AccountStatus.BLOCKED)
        .disabled(user.getStatus() != AccountStatus.ACTIVE)
        .build();
  }
}
