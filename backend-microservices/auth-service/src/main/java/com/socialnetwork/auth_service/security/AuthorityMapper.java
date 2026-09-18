package com.socialnetwork.auth_service.security;

import static com.socialnetwork.common.constants.SecurityConstants.ROLE_PREFIX;

import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import java.util.List;
import java.util.TreeSet;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Single place that turns a credential's roles and permissions into the authorities carried by the
 * JWT {@code roles} claim: {@code ROLE_<role>} for every role plus every permission name (e.g.
 * {@code REPORT:CREATE}). Roles are stored without prefix; the prefix is added here only.
 */
@Component
public class AuthorityMapper {

  public List<String> authorities(UserCredential credential) {
    TreeSet<String> authorities = new TreeSet<>();
    for (Role role : credential.getRoles()) {
      authorities.add(ROLE_PREFIX + role.getName());
      for (Permission permission : role.getPermissions()) {
        authorities.add(permission.getName());
      }
    }
    return List.copyOf(authorities);
  }

  public List<GrantedAuthority> grantedAuthorities(UserCredential credential) {
    return authorities(credential).stream()
        .map(a -> (GrantedAuthority) new SimpleGrantedAuthority(a))
        .toList();
  }

  /** Role names without prefix, as shown to the frontend in login/refresh responses. */
  public List<String> roleNames(UserCredential credential) {
    return credential.getRoles().stream().map(Role::getName).sorted().toList();
  }
}
