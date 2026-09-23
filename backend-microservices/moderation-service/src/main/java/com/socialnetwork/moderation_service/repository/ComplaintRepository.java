package com.socialnetwork.moderation_service.repository;

import com.socialnetwork.common.dto.IdCount;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.moderation_service.enums.ComplaintStatus;
import com.socialnetwork.moderation_service.model.Complaint;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComplaintRepository
    extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {

  /**
   * A user may only have one open complaint per target; once it is decided they may file again.
   */
  boolean existsByUserIdAndTargetTypeAndTargetIdAndStatus(
      Long userId, TargetType targetType, String targetId, ComplaintStatus status);

  Page<Complaint> findByTargetTypeAndTargetId(
      TargetType targetType, String targetId, Pageable pageable);

  @Query(
      "SELECT c.targetId as id, COUNT(c) as count "
          + "FROM Complaint c "
          + "WHERE c.targetType = :type AND c.targetId IN :ids "
          + "GROUP BY c.targetId")
  List<IdCount> countByTargetTypeAndTargetIdIn(
      @Param("type") TargetType type, @Param("ids") List<String> ids);
}
