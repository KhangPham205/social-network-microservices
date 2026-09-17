package com.socialnetwork.moderation_service.repository;

import com.socialnetwork.moderation_service.model.ModerationLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vo.TargetType;

@Repository
public interface ModerationLogRepository
    extends JpaRepository<ModerationLog, Long>, JpaSpecificationExecutor<ModerationLog> {
  @Query(
      "SELECT m FROM ModerationLog m "
          + "WHERE m.targetType = :type "
          + "AND m.targetId = :id "
          + "AND (:filter IS NULL OR :filter = '' OR "
          + "     LOWER(m.reason) LIKE LOWER(CONCAT('%', :filter, '%')) OR "
          + "     LOWER(m.action) LIKE LOWER(CONCAT('%', :filter, '%')))"
          + "ORDER BY m.createdAt DESC")
  Page<ModerationLog> findHistory(
      @Param("type") TargetType type,
      @Param("id") String id,
      @Param("filter") String filter,
      Pageable pageable);

  @Query("SELECT m.reason, COUNT(m) FROM ModerationLog m GROUP BY m.reason")
  List<Object[]> countByReason();

  // Count auto-bans (actorId is null) vs manual bans (actorId is not null)
  @Query("SELECT COUNT(m) FROM ModerationLog m WHERE m.actorId IS NULL")
  long countAutoBanned();

  @Query("SELECT COUNT(m) FROM ModerationLog m WHERE m.actorId IS NOT NULL")
  long countManualBanned();
}
