package com.socialnetwork.auth_service.component;

import com.socialnetwork.auth_service.model.Permission;
import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.repository.PermissionRepository;
import com.socialnetwork.auth_service.repository.RoleRepository;
import com.socialnetwork.common.constants.SecurityConstants;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the roles and permissions the platform cannot work without.
 *
 * <p>The permission names below are the authorities that {@code @PreAuthorize} expressions in
 * user-service and moderation-service check. They end up in the JWT {@code roles} claim through
 * {@code AuthorityMapper}, so without these rows an administrator is authenticated but gets 403 on
 * every administrative endpoint.
 *
 * <p>Idempotent: existing rows are kept and missing ones are added, so it is safe to run on every
 * start. Permissions are granted to ADMIN only; the USER role gets none.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

  private static final Map<String, String> DEFAULT_ROLES =
      Map.of(
          SecurityConstants.ROLE_USER, "Standard end user",
          SecurityConstants.ROLE_ADMIN, "Full administrative access");

  /** {@code RESOURCE:ACTION -> description}, all granted to ADMIN. */
  private static final Map<String, String> ADMIN_PERMISSIONS = adminPermissions();

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  private static Map<String, String> adminPermissions() {
    Map<String, String> permissions = new LinkedHashMap<>();
    // user-service administration
    permissions.put("USER:READ_ALL", "List and read any user profile");
    permissions.put("USER:UPDATE_ANY", "Update any user profile, role or status");
    permissions.put("USER:READ_SENSITIVE", "Read e-mail and account status of any user");
    permissions.put("USER:BLOCK", "Block or unblock a user account");
    // moderation-service
    permissions.put("MODERATION:ACCESS", "Access the moderation dashboards");
    permissions.put("REPORT:VIEW_ALL", "Read every report");
    permissions.put("REPORT:PROCESS", "Approve or reject reports");
    permissions.put("COMPLAINT:VIEW_ALL", "Read every complaint");
    permissions.put("COMPLAINT:PROCESS", "Approve or reject complaints");
    permissions.put("POST:DELETE_ANY", "Block or restore any post or comment");
    permissions.put("MESSAGE:READ_ANY", "Read any chat message for moderation");
    return permissions;
  }

  @Override
  @Transactional
  public void run(String... args) {
    seedRoles();
    Set<Permission> permissions = seedPermissions();
    grantToAdmin(permissions);
  }

  private void seedRoles() {
    DEFAULT_ROLES.forEach(
        (name, description) ->
            roleRepository
                .findByName(name)
                .orElseGet(
                    () -> {
                      log.info("Seeding missing role {}", name);
                      return roleRepository.save(
                          Role.builder().name(name).description(description).build());
                    }));
  }

  private Set<Permission> seedPermissions() {
    List<Permission> existing = permissionRepository.findAll();
    Map<String, Permission> byName =
        existing.stream().collect(Collectors.toMap(Permission::getName, p -> p));

    ADMIN_PERMISSIONS.forEach(
        (name, description) -> {
          if (byName.containsKey(name)) {
            return;
          }
          String[] parts = name.split(Permission.SEPARATOR, 2);
          log.info("Seeding missing permission {}", name);
          byName.put(
              name,
              permissionRepository.save(
                  Permission.builder()
                      .resource(parts[0])
                      .action(parts[1])
                      .name(name)
                      .description(description)
                      .build()));
        });

    return ADMIN_PERMISSIONS.keySet().stream().map(byName::get).collect(Collectors.toSet());
  }

  private void grantToAdmin(Set<Permission> permissions) {
    roleRepository
        .findByName(SecurityConstants.ROLE_ADMIN)
        .ifPresent(
            admin -> {
              // Permission has no value-based equals, so compare on the unique name.
              Set<String> granted =
                  admin.getPermissions().stream()
                      .map(Permission::getName)
                      .collect(Collectors.toSet());
              List<Permission> missing =
                  permissions.stream().filter(p -> !granted.contains(p.getName())).toList();
              if (missing.isEmpty()) {
                return;
              }
              admin.getPermissions().addAll(missing);
              roleRepository.save(admin);
              log.info("Granted {} new permissions to role {}", missing.size(), admin.getName());
            });
  }
}
