package com.socialnetwork.auth_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "permissions",
    // Đảm bảo không thể có 2 quyền "POST:READ"
    uniqueConstraints = @UniqueConstraint(columnNames = {"resource", "action"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String resource; // Ví dụ: "POST", "USER", "COMMENT", "ADMIN_DASHBOARD"

  @Column(nullable = false)
  private String action; // Ví dụ: "CREATE", "READ", "UPDATE", "DELETE", "MODERATE"

  /** Tên quyền đầy đủ (ví dụ: "POST:READ", "USER:DELETE") */
  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  /**
   * Đây là một "trick" của JPA. Trước khi lưu (persist) một Permission mới, nó sẽ tự động tạo ra
   * trường 'name' chuẩn hóa.
   */
  @PrePersist
  public void generateName() {
    if (this.name == null) {
      this.name = this.resource.toUpperCase() + ":" + this.action.toUpperCase();
    }
  }
}
