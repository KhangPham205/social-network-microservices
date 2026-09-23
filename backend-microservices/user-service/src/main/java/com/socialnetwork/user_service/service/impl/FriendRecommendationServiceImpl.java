package com.socialnetwork.user_service.service.impl;

import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.repository.neo4j.RecommendedFriendProjection;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import com.socialnetwork.user_service.service.FriendRecommendationService;
import com.socialnetwork.user_service.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendRecommendationServiceImpl implements FriendRecommendationService {

  private final UserNodeRepository userNodeRepository;
  private final UserService userService;

  @Override
  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFriendRecommendations(Long userId, Pageable pageable) {
    Page<RecommendedFriendProjection> page =
        userNodeRepository.findFriendRecommendations(userId, pageable);

    List<Long> targetIds =
        page.getContent().stream().map(RecommendedFriendProjection::getId).toList();

    // The graph only knows ids; the profile and the relation flags come from PostgreSQL.
    Map<Long, UserRelationDto> byId =
        userService.getRelationsWithUsers(targetIds).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(UserRelationDto::getId, dto -> dto, (a, b) -> a));

    List<UserRelationDto> content =
        page.getContent().stream()
            .map(
                projection -> {
                  UserRelationDto dto = byId.get(projection.getId());
                  if (dto != null) {
                    dto.setMutualFriendsCount(projection.getMutualFriendsCount());
                  }
                  return dto;
                })
            .filter(Objects::nonNull)
            .toList();

    return PageVO.<UserRelationDto>builder()
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .content(content)
        .build();
  }
}
