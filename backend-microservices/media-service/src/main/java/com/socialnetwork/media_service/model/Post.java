package com.socialnetwork.media_service.model;

import com.socialnetwork.media_service.enums.AccessScope;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<Map<String, String>> media;

  @Enumerated(EnumType.STRING)
  private AccessScope accessModifier;

  // Thay User bằng UserCache
  @ManyToOne(fetch = FetchType.EAGER) // EAGER vì luôn cần hiển thị tên tác giả
  @JoinColumn(name = "author_id", nullable = false)
  private UserCache author;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shared_post_id")
  private Post sharedPost;

  private int reactCount;
  private int commentCount;
  private int shareCount;

  @Column(name = "is_system_ban")
  private Boolean isSystemBan;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  private Instant createdAt;
  private Instant updatedAt;
}
