package entity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import java.time.Instant;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Accessors(chain = true)
@MappedSuperclass
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Nullable
  @CreationTimestamp
  private Instant createdAt;

  @Nullable @UpdateTimestamp
  private Instant updatedAt;

  @Nullable private String createdBy;

  @Nullable private String updatedBy;
}
