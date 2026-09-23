package com.socialnetwork.moderation_service.repository;

import com.socialnetwork.common.dto.IdCount;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ReportSource;
import com.socialnetwork.moderation_service.model.Report;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository
    extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

  /** One user may report the same target only once. */
  boolean existsByReporterIdAndTargetTypeAndTargetId(
      Long reporterId, TargetType targetType, String targetId);

  /** Idempotency guard for the AI pipeline: a target is auto-reported at most once. */
  boolean existsByTargetTypeAndTargetIdAndSource(
      TargetType targetType, String targetId, ReportSource source);

  long countByTargetUserId(Long userId);

  Page<Report> findByTargetUserId(Long userId, Pageable pageable);

  Page<Report> findByTargetTypeAndTargetId(
      TargetType targetType, String targetId, Pageable pageable);

  @Query(
      "SELECT r.targetUserId AS id, COUNT(r) AS count FROM Report r "
          + "GROUP BY r.targetUserId ORDER BY COUNT(r) DESC")
  Page<IdCount> findTopReportedUsers(Pageable pageable);

  @Query(
      "SELECT r.targetId AS id, COUNT(r) AS count FROM Report r WHERE r.targetType = :type "
          + "GROUP BY r.targetId ORDER BY COUNT(r) DESC")
  Page<IdCount> findTopReportedTargets(@Param("type") TargetType type, Pageable pageable);

  @Query(
      "SELECT r.targetId AS id, COUNT(r) AS count FROM Report r "
          + "WHERE r.targetType = :type AND r.targetId IN :ids "
          + "GROUP BY r.targetId")
  List<IdCount> countByTargetTypeAndTargetIdIn(
      @Param("type") TargetType type, @Param("ids") List<String> ids);
}
