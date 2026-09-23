package com.socialnetwork.media_service.service;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.dto.react.ReactRequest;
import com.socialnetwork.media_service.dto.react.ReactResponse;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.dto.react.ReactUserDto;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;

public interface ReactService {

  ReactResponse toggleReact(ReactRequest req);

  /** Who reacted to a target; the caller must be allowed to see that target. */
  PageVO<ReactUserDto> getReactUsers(Long targetId, TargetType targetType, Pageable pageable);

  /** Summary for a target the caller has already been authorised for (internal rendering). */
  ReactSummaryDto getReactSummary(Long targetId, TargetType targetType, Long viewerId);

  /** Summary for the current user, checking view permission on the target first. */
  ReactSummaryDto getVisibleReactSummary(Long targetId, TargetType targetType);

  Map<Long, ReactSummaryDto> getReactSummaries(
      List<Long> targetIds, Long viewerId, TargetType targetType);
}
