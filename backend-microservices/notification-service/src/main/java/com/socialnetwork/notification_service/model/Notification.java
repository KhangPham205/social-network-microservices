package com.socialnetwork.notification_service.model;

import com.socialnetwork.notification_service.enums.NotificationType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long receiverId; // ID người nhận (chỉ cần ID để query cho lẹ)

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "actor_id", nullable = false)
  private UserCache actor; // Người thả tim/comment

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType type;

  private String content;
  private String link;

  @Builder.Default private boolean isRead = false;

  @CreationTimestamp private Instant createdAt;
}
