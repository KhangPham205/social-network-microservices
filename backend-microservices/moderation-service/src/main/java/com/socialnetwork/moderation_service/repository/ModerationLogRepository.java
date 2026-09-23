package com.socialnetwork.moderation_service.repository;

import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.model.ModerationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationLogRepository
    extends JpaRepository<ModerationLog, Long>, JpaSpecificationExecutor<ModerationLog> {

  @Query(
      "SELECT m FROM ModerationLog m "
          + "WHERE m.targetType = :type "
          + "AND m.targetId = :id "
          + "AND (:filter IS NULL OR :filter = '' "
          + "     OR LOWER(m.reason) LIKE LOWER(CONCAT('%', :filter, '%')) "
          + "     OR LOWER(CAST(m.action AS string)) LIKE LOWER(CONCAT('%', :filter, '%'))) "
          + "ORDER BY m.createdAt DESC")
  Page<ModerationLog> findHistory(
      @Param("type") TargetType type,
      @Param("id") String id,
      @Param("filter") String filter,
      Pageable pageable);
}
