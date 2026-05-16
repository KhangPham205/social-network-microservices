package com.socialnetwork.moderation_service.model;

import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import vo.TargetType;

@Entity
@Table(name = "complaints")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint extends BaseEntity {

    @Column(name = "status")
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType; // POST, COMMENT, USER

    @Column(nullable = false)
    private String targetId; // ID of post/comment/user

    @Column(name = "user_id", nullable = false)
    private Long userId; // ID of complainant (not entity reference)

    @Column(columnDefinition = "TEXT")
    private String content; // Reason for complaint

    @Column(columnDefinition = "TEXT")
    private String adminResponse;
}