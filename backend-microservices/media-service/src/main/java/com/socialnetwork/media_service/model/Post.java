package com.socialnetwork.media_service.model;

import com.socialnetwork.media_service.enums.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  // Hibernate picks jsonb on PostgreSQL and json on H2 from the JDBC type alone.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "media")
  private List<Map<String, String>> media;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_modifier", nullable = false, length = 16)
  private AccessScope accessModifier;

  /** Read model of the author, kept in sync from Kafka; never a foreign service entity. */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "author_id", nullable = false)
  private UserCache author;

  /** The original post when this one is a share. A post may be shared any number of times. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shared_post_id")
  private Post sharedPost;

  private int reactCount;
  private int commentCount;
  private int shareCount;

  /** Set by moderation-service through {@code MODERATION_ACTIONS}. */
  @Builder.Default
  @Column(name = "is_system_ban", nullable = false)
  private Boolean isSystemBan = false;

  /** Soft delete marker: rows are never removed, so shares and comments keep their foreign keys. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
