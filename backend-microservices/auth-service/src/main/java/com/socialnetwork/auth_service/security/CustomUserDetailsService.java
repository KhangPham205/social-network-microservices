package com.socialnetwork.auth_service.security;

import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import com.socialnetwork.auth_service.repository.UserCredentialRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserCredentialRepository userCredentialRepository;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserCredential user =
        userCredentialRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    Set<Role> roles = user.getRoles();
    Set<String> roleNames = new HashSet<>();
    Set<String> permissionNames = new HashSet<>();

    for (Role role : roles) {
      roleNames.add(role.getName().replace("ROLE_", ""));

      role.getPermissions().forEach(permission -> permissionNames.add(permission.getName()));
    }

    return User.builder()
        .username(user.getUsername())
        .password(user.getPassword())
        .roles(roleNames.toArray(new String[0]))
        .authorities(permissionNames.toArray(new String[0]))
        .build();
  }
}
