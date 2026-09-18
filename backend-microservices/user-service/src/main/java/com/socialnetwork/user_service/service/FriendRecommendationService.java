package com.socialnetwork.user_service.service;

import com.socialnetwork.user_service.dto.UserRelationDto;
import com.socialnetwork.user_service.repository.neo4j.UserNodeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.socialnetwork.common.vo.PageVO;

@Service
@RequiredArgsConstructor
public class FriendRecommendationService {

  private final UserNodeRepository userNodeRepository;
  private final UserService userService;

  @Transactional(readOnly = true)
  public PageVO<UserRelationDto> getFriendRecommendations(Long userId, Pageable pageable) {
    Page<com.socialnetwork.user_service.repository.neo4j.RecommendedFriendProjection> page =
        userNodeRepository.findFriendRecommendations(userId, pageable);

    List<Long> targetIds =
        page.getContent().stream()
            .map(com.socialnetwork.user_service.repository.neo4j.RecommendedFriendProjection::getId)
            .toList();

    List<UserRelationDto> dtos = userService.getRelationsWithUsers(targetIds);
    java.util.Map<Long, UserRelationDto> dtoMap =
        dtos.stream()
            .collect(java.util.stream.Collectors.toMap(UserRelationDto::getId, dto -> dto));

    List<UserRelationDto> content =
        page.getContent().stream()
            .map(
                projection -> {
                  UserRelationDto dto = dtoMap.get(projection.getId());
                  if (dto != null) {
                    dto.setMutualFriendsCount(projection.getMutualFriendsCount());
                  }
                  return dto;
                })
            .filter(java.util.Objects::nonNull)
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
