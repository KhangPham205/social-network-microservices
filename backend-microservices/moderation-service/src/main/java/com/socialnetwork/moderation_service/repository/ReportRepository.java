package com.socialnetwork.moderation_service.repository;

import com.socialnetwork.moderation_service.enums.ReportStatus;
import com.socialnetwork.moderation_service.model.Report;
import dto.IdCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vo.TargetType;

import java.util.List;
import java.util.Set;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, TargetType targetType, String targetId);

    long countByTargetUserId(Long userId);

    Page<Report> findByTargetUserId(Long userId, Pageable pageable);

    Page<Report> findByTargetTypeAndTargetId(TargetType targetType, String targetId, Pageable pageable);

    @Query("SELECT r.targetId AS id, COUNT(r) AS count FROM Report r " +
            "WHERE r.targetType = :type AND r.targetId IN :ids " +
            "GROUP BY r.targetId")
    List<IdCount> countByTargetTypeAndTargetIdIn(@Param("type") TargetType type, @Param("ids") List<String> ids);

    boolean existsByTargetIdAndTargetTypeAndIsBannedBySystemIsNotNull(String targetId, TargetType targetType);

    long countByStatus(ReportStatus status);

    @Query("SELECT r.targetId FROM Report r WHERE r.targetType = :type AND r.targetId IN :ids")
    Set<String> findReportedTargetIds(@Param("type") TargetType type, @Param("ids") List<String> ids);

    @Query(value = """
        SELECT CAST(EXTRACT(MONTH FROM created_at) AS INTEGER) as time_unit, COUNT(*) 
        FROM reports 
        WHERE EXTRACT(YEAR FROM created_at) = :year 
        GROUP BY time_unit
    """, nativeQuery = true)
    List<Object[]> countByYear(@Param("year") int year);

    @Query(value = """
        SELECT CAST(EXTRACT(DAY FROM created_at) AS INTEGER) as time_unit, COUNT(*) 
        FROM reports 
        WHERE EXTRACT(MONTH FROM created_at) = :month 
          AND EXTRACT(YEAR FROM created_at) = :year 
        GROUP BY time_unit
    """, nativeQuery = true)
    List<Object[]> countByMonth(@Param("month") int month, @Param("year") int year);
}