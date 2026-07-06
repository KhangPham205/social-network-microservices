package com.socialnetwork.media_service.service;

import com.socialnetwork.media_service.dto.react.ReactRequest;
import com.socialnetwork.media_service.dto.react.ReactResponse;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.dto.react.ReactUserDto;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import vo.PageVO;
import vo.TargetType;

public interface ReactService {
  ReactResponse toggleReact(ReactRequest req);

  PageVO<ReactUserDto> getReactUsers(Long targetId, TargetType targetType, Pageable pageable);

  ReactSummaryDto getReactSummary(Long targetId, TargetType targetType, Long userId);

  Map<Long, ReactSummaryDto> getReactSummaries(
      List<Long> targetIds, Long viewerId, TargetType targetType);
}
