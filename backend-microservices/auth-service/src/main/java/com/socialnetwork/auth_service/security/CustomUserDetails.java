package com.socialnetwork.auth_service.security;

import com.socialnetwork.auth_service.enums.AccountStatus;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

  private final UserCredential userCredential;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    Set<GrantedAuthority> authorities = new HashSet<>();

    for (Role role : userCredential.getRoles()) {
      // thêm quyền role
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));

      // thêm quyền permission
      role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
    }

    return authorities;
  }

  @Override
  public String getPassword() {
    return userCredential.getPassword();
  }

  @Override
  public String getUsername() {
    return userCredential.getUsername();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return userCredential.getStatus() == AccountStatus.ACTIVE;
  }

  public UserCredential getUser() {
    return userCredential;
  }
}
