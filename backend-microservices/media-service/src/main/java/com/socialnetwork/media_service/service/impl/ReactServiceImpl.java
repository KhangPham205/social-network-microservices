package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.common.constants.KafkaTopics;
import com.socialnetwork.common.events.NotificationEvent;
import com.socialnetwork.common.exception.BadRequestException;
import com.socialnetwork.common.exception.ResourceNotFoundException;
import com.socialnetwork.common.security.SecurityUtils;
import com.socialnetwork.common.vo.NotificationType;
import com.socialnetwork.common.vo.PageVO;
import com.socialnetwork.common.vo.TargetType;
import com.socialnetwork.media_service.dto.react.ReactRequest;
import com.socialnetwork.media_service.dto.react.ReactResponse;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.dto.react.ReactUserDto;
import com.socialnetwork.media_service.model.React;
import com.socialnetwork.media_service.model.ReactType;
import com.socialnetwork.media_service.model.UserCache;
import com.socialnetwork.media_service.repository.CommentRepository;
import com.socialnetwork.media_service.repository.PostRepository;
import com.socialnetwork.media_service.repository.ReactRepository;
import com.socialnetwork.media_service.repository.ReactTypeRepository;
import com.socialnetwork.media_service.repository.UserCacheRepository;
import com.socialnetwork.media_service.service.ContentAccessService;
import com.socialnetwork.media_service.service.KafkaEventPublisher;
import com.socialnetwork.media_service.service.ReactService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactServiceImpl implements ReactService {

  private final ReactRepository reactRepository;
  private final ReactTypeRepository reactTypeRepository;
  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final UserCacheRepository userCacheRepository;
  private final ContentAccessService contentAccessService;
  private final KafkaEventPublisher eventPublisher;

  @Override
  @Transactional
  public ReactResponse toggleReact(ReactRequest req) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    UserCache user =
        userCacheRepository
            .findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User cache not found"));

    TargetType targetType = req.getTargetType();
    Long targetId = req.getTargetId();
    if (targetType == null || targetId == null || req.getReactTypeId() == null) {
      throw new BadRequestException("targetId, targetType and reactTypeId are required");
    }
    // Reacting is a read of the target too: only what the user may see can be reacted to.
    contentAccessService.requireViewPermissionOnTarget(targetId, targetType, currentUserId);

    ReactType reactType =
        reactTypeRepository
            .findById(req.getReactTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("React type not found"));

    React existing =
        reactRepository.findByUserAndTargetIdAndTargetType(user, targetId, targetType).orElse(null);
    boolean isNewReact = false;

    if (existing == null) {
      reactRepository.save(
          React.builder()
              .user(user)
              .targetId(targetId)
              .targetType(targetType)
              .reactType(reactType)
              .build());
      isNewReact = true;
    } else if (existing.getReactType().getId().equals(reactType.getId())) {
      reactRepository.delete(existing);
    } else {
      existing.setReactType(reactType);
      reactRepository.save(existing);
    }

    long count = reactRepository.countByTargetIdAndTargetType(targetId, targetType);
    updateTargetReactCount(targetType, targetId, count);

    if (isNewReact) {
      notifyTargetOwner(targetType, targetId, currentUserId);
    }

    return ReactResponse.builder()
        .targetId(targetId)
        .targetType(targetType)
        .reactCount(count)
        .message("React updated")
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReactUserDto> getReactUsers(
      Long targetId, TargetType targetType, Pageable pageable) {
    contentAccessService.requireViewPermissionOnTarget(
        targetId, targetType, SecurityUtils.getCurrentUserId());

    Page<React> page = reactRepository.findByTargetIdAndTargetType(targetId, targetType, pageable);
    List<ReactUserDto> content =
        page.stream()
            .map(
                react ->
                    ReactUserDto.builder()
                        .userId(react.getUser().getId())
                        .displayName(react.getUser().getDisplayName())
                        .avatarUrl(react.getUser().getAvatarUrl())
                        .reactTypeId(react.getReactType().getId())
                        .reactTypeName(react.getReactType().getName())
                        .build())
            .toList();

    return PageVO.<ReactUserDto>builder()
        .content(content)
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .numberOfElements(content.size())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReactSummaryDto getReactSummary(Long targetId, TargetType targetType, Long viewerId) {
    List<Object[]> summary = reactRepository.summarizeByTarget(targetId, targetType);
    Map<String, Long> counts =
        summary.stream().collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

    return ReactSummaryDto.builder()
        .counts(counts)
        .total(counts.values().stream().mapToLong(Long::longValue).sum())
        .currentUserReact(
            reactRepository
                .findViewerReactNameForTarget(viewerId, targetId, targetType)
                .orElse(null))
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReactSummaryDto getVisibleReactSummary(Long targetId, TargetType targetType) {
    Long viewerId = SecurityUtils.getCurrentUserId();
    contentAccessService.requireViewPermissionOnTarget(targetId, targetType, viewerId);
    return getReactSummary(targetId, targetType, viewerId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, ReactSummaryDto> getReactSummaries(
      List<Long> targetIds, Long viewerId, TargetType targetType) {
    if (targetIds == null || targetIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, Map<String, Long>> allCounts = new HashMap<>();
    for (Object[] row : reactRepository.summarizeByTargetList(targetIds, targetType)) {
      Long targetId = (Long) row[0];
      allCounts.computeIfAbsent(targetId, key -> new HashMap<>()).put((String) row[1], (Long) row[2]);
    }

    Map<Long, String> viewerReacts =
        reactRepository.findViewerReactsForTargetList(viewerId, targetIds, targetType).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1], (a, b) -> a));

    Map<Long, ReactSummaryDto> result = new HashMap<>();
    for (Long targetId : targetIds) {
      Map<String, Long> counts = allCounts.getOrDefault(targetId, Map.of());
      long total = counts.values().stream().mapToLong(Long::longValue).sum();
      result.put(targetId, new ReactSummaryDto(counts, total, viewerReacts.get(targetId)));
    }
    return result;
  }

  // ---------------------------------------------------------------- helpers

  private void updateTargetReactCount(TargetType targetType, Long targetId, long count) {
    switch (targetType) {
      case POST ->
          postRepository
              .findById(targetId)
              .ifPresent(
                  post -> {
                    post.setReactCount((int) count);
                    postRepository.save(post);
                  });
      case COMMENT ->
          commentRepository
              .findById(targetId)
              .ifPresent(
                  comment -> {
                    comment.setReactCount((int) count);
                    commentRepository.save(comment);
                  });
      default -> log.debug("React count is not tracked for {}", targetType);
    }
  }

  private void notifyTargetOwner(TargetType targetType, Long targetId, Long actorId) {
    Long ownerId =
        switch (targetType) {
          case POST -> postRepository.findById(targetId).map(p -> p.getAuthor().getId()).orElse(null);
          case COMMENT ->
              commentRepository.findById(targetId).map(c -> c.getAuthor().getId()).orElse(null);
          default -> null;
        };
    if (ownerId == null || ownerId.equals(actorId)) {
      return;
    }

    NotificationType type =
        targetType == TargetType.POST ? NotificationType.REACT_POST : NotificationType.REACT_COMMENT;
    Long postId =
        targetType == TargetType.POST
            ? targetId
            : commentRepository.findById(targetId).map(c -> c.getPost().getId()).orElse(targetId);

    eventPublisher.publishAfterCommit(
        KafkaTopics.NOTIFICATION,
        String.valueOf(ownerId),
        NotificationEvent.of(actorId, ownerId, type, targetId, postId));
  }
}
