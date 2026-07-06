package com.socialnetwork.auth_service.component;

import com.socialnetwork.auth_service.model.Role;
import com.socialnetwork.auth_service.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

  private final RoleRepository roleRepository;

  @Override
  public void run(String... args) throws Exception {
    seedRoles();
  }

  private void seedRoles() {
    if (roleRepository.count() == 0) {
      log.info("Bảng roles đang trống. Bắt đầu tạo dữ liệu mặc định...");

      Role roleUser = new Role();
      roleUser.setName("USER");
      roleRepository.save(roleUser);

      Role roleAdmin = new Role();
      roleAdmin.setName("ADMIN");
      roleRepository.save(roleAdmin);

      log.info("Đã tạo xong các Role mặc định.");
    }
  }
}
