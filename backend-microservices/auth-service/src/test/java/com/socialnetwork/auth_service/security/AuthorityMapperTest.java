package com.socialnetwork.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.model.UserCredential;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/** The {@code roles} claim must carry ROLE_&lt;name&gt; for every role plus every permission name. */
class AuthorityMapperTest {

  private final AuthorityMapper mapper = new AuthorityMapper();

  private static Permission permission(String resource, String action) {
    return Permission.builder()
        .resource(resource)
        .action(action)
        .name(Permission.nameOf(resource, action))
        .build();
  }

  private static UserCredential credential(Role... roles) {
    return UserCredential.builder().username("alice").password("x").roles(Set.of(roles)).build();
  }

  @Test
  @DisplayName("every role becomes ROLE_<name> and every permission is added verbatim")
  void mapsRolesAndPermissions() {
    Role admin =
        Role.builder()
            .name("ADMIN")
            .permissions(Set.of(permission("USER", "CREATE"), permission("USER", "DELETE")))
            .build();
    Role user = Role.builder().name("USER").permissions(Set.of(permission("POST", "READ"))).build();

    assertThat(mapper.authorities(credential(admin, user)))
        .containsExactly("POST:READ", "ROLE_ADMIN", "ROLE_USER", "USER:CREATE", "USER:DELETE");
  }

  @Test
  @DisplayName("a role without permissions still yields its ROLE_ authority")
  void mapsRoleWithoutPermissions() {
    Role user = Role.builder().name("USER").permissions(Set.of()).build();

    assertThat(mapper.authorities(credential(user))).containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("a permission shared by two roles is emitted once")
  void deduplicatesSharedPermissions() {
    Permission shared = permission("POST", "READ");
    Role admin = Role.builder().name("ADMIN").permissions(Set.of(shared)).build();
    Role user = Role.builder().name("USER").permissions(Set.of(shared)).build();

    assertThat(mapper.authorities(credential(admin, user)))
        .containsExactly("POST:READ", "ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  @DisplayName("grantedAuthorities mirrors authorities(), roleNames() stays unprefixed")
  void exposesGrantedAuthoritiesAndPlainRoleNames() {
    Role admin = Role.builder().name("ADMIN").permissions(Set.of(permission("USER", "CREATE"))).build();
    UserCredential credential = credential(admin);

    assertThat(mapper.grantedAuthorities(credential))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyElementsOf(mapper.authorities(credential));
    assertThat(mapper.roleNames(credential)).containsExactly("ADMIN");
  }
}
