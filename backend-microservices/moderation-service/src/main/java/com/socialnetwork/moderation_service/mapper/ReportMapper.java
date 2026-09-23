package com.socialnetwork.moderation_service.mapper;

import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.model.Complaint;
import com.socialnetwork.moderation_service.model.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {

  @Mapping(source = "bannedBySystem", target = "isBannedBySystem")
  @Mapping(target = "reporterName", ignore = true)
  @Mapping(target = "reporterAvatar", ignore = true)
  ReportResponse toResponse(Report report);

  @Mapping(target = "userDisplayName", ignore = true)
  ComplaintResponse toResponse(Complaint complaint);
}
