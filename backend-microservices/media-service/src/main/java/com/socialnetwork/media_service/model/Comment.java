package com.socialnetwork.media_service.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
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
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Read model of the author, kept in sync from Kafka. */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "author_id", nullable = false)
  private UserCache author;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "post_id", nullable = false)
  private Post post;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Comment parent;

  @Builder.Default
  @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
  private List<Comment> replies = new ArrayList<>();

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "media")
  private List<Map<String, String>> media;

  private int reactCount;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  /** Soft delete marker: replies keep pointing at a deleted parent. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Builder.Default
  @Column(name = "is_system_ban", nullable = false)
  private Boolean isSystemBan = false;
}
