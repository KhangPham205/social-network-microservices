package com.socialnetwork.auth_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"resource", "action"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

  public static final String SEPARATOR = ":";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** e.g. "POST", "USER", "REPORT". */
  @Column(nullable = false)
  private String resource;

  /** e.g. "CREATE", "READ", "UPDATE", "DELETE". */
  @Column(nullable = false)
  private String action;

  /** Authority name carried in the JWT: {@code RESOURCE:ACTION}. */
  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  public static String nameOf(String resource, String action) {
    return resource.toUpperCase() + SEPARATOR + action.toUpperCase();
  }
}
