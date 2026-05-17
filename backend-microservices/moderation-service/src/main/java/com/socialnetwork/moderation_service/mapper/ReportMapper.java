package com.socialnetwork.moderation_service.mapper;

import com.socialnetwork.moderation_service.dto.ComplaintResponse;
import com.socialnetwork.moderation_service.dto.ReportResponse;
import com.socialnetwork.moderation_service.model.Complaint;
import com.socialnetwork.moderation_service.model.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {

  // Map Report -> ReportResponse
  @Mapping(source = "reporterId", target = "reporterId")
  @Mapping(source = "status", target = "status")
  ReportResponse toResponse(Report report);

  // Map Complaint -> ComplaintResponse
  @Mapping(source = "userId", target = "userId")
  ComplaintResponse toResponse(Complaint complaint);
}
