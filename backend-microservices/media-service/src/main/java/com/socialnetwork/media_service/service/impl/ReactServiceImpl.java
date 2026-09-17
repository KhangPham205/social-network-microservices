package com.socialnetwork.media_service.service.impl;

import com.socialnetwork.media_service.dto.react.ReactRequest;
import com.socialnetwork.media_service.dto.react.ReactResponse;
import com.socialnetwork.media_service.dto.react.ReactSummaryDto;
import com.socialnetwork.media_service.dto.react.ReactUserDto;
import com.socialnetwork.media_service.model.*;
import com.socialnetwork.media_service.repository.*;
import com.socialnetwork.media_service.service.ReactService;
import exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vo.PageVO;
import vo.TargetType;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactServiceImpl implements ReactService {

  private final ReactRepository reactRepository;
  private final ReactTypeRepository reactTypeRepository;
  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final UserCacheRepository userCacheRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Override
  @Transactional
  public ReactResponse toggleReact(ReactRequest req) {
    Long currentUserId =
        Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    UserCache user =
        userCacheRepository
            .findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User cache not found"));

    TargetType tType = req.getTargetType();
    Long targetId = req.getTargetId();

    // Kiểm tra tồn tại target
    boolean exists =
        switch (tType) {
          case POST -> postRepository.existsById(targetId);
          case COMMENT -> commentRepository.existsById(targetId);
          default -> true;
        };
    if (!exists) throw new ResourceNotFoundException("Target not found");

    React existing =
        reactRepository.findByUserAndTargetIdAndTargetType(user, targetId, tType).orElse(null);
    boolean isNewReact = false;
    Long targetOwnerId = null;

    if (existing != null) {
      if (existing.getReactType().getId().equals(req.getReactTypeId())) {
        reactRepository.delete(existing); // Cùng loại -> Xóa
      } else {
        ReactType newType = reactTypeRepository.getReferenceById(req.getReactTypeId());
        existing.setReactType(newType);
        reactRepository.save(existing); // Khác loại -> Đổi
      }
    } else {
      ReactType rt = reactTypeRepository.getReferenceById(req.getReactTypeId());
      reactRepository.save(
          React.builder()
              .user(user)
              .targetId(targetId)
              .targetType(tType)
              .reactType(rt)
              .createdAt(Instant.now())
              .build());
      isNewReact = true;
    }

    // Cập nhật biến đếm cho Post hoặc Comment
    long count = reactRepository.countByTargetIdAndTargetType(targetId, tType);
    updateTargetReactCount(tType, targetId, count);

    // Lấy ID người nhận thông báo
    if (isNewReact) {
      if (tType == TargetType.POST) {
        targetOwnerId =
            postRepository.findById(targetId).map(p -> p.getAuthor().getId()).orElse(null);
      } else if (tType == TargetType.COMMENT) {
        targetOwnerId =
            commentRepository.findById(targetId).map(c -> c.getAuthor().getId()).orElse(null);
      }

      // Bắn Kafka Event cho Notification Service
      if (targetOwnerId != null && !targetOwnerId.equals(currentUserId)) {
        String notificationType = tType == TargetType.POST ? "REACT_POST" : "REACT_COMMENT";
        kafkaTemplate.send(
            "notification-topic",
            new events.NotificationEvent(
                currentUserId, targetOwnerId, notificationType, targetId, targetId));
        log.info(
            "✅ Sent notification event: {} from {} to {}",
            notificationType,
            currentUserId,
            targetOwnerId);
      }
    }

    return ReactResponse.builder()
        .targetId(targetId)
        .targetType(tType)
        .reactCount(count)
        .message("React updated")
        .build();
  }

  private void updateTargetReactCount(TargetType tType, Long targetId, long count) {
    switch (tType) {
      case POST ->
          postRepository
              .findById(targetId)
              .ifPresent(
                  p -> {
                    p.setReactCount((int) count);
                    postRepository.save(p);
                  });
      case COMMENT ->
          commentRepository
              .findById(targetId)
              .ifPresent(
                  c -> {
                    c.setReactCount((int) count);
                    commentRepository.save(c);
                  });
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PageVO<ReactUserDto> getReactUsers(
      Long targetId, TargetType targetType, Pageable pageable) {
    Page<React> page = reactRepository.findByTargetIdAndTargetType(targetId, targetType, pageable);

    List<ReactUserDto> content =
        page.stream()
            .map(
                r ->
                    ReactUserDto.builder()
                        .userId(r.getUser().getId())
                        .displayName(r.getUser().getDisplayName())
                        .avatarUrl(r.getUser().getAvatarUrl())
                        .reactTypeId(r.getReactType().getId())
                        .reactTypeName(r.getReactType().getName())
                        .build())
            .toList();

    return PageVO.<ReactUserDto>builder()
        .content(content)
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public ReactSummaryDto getReactSummary(Long targetId, TargetType targetType, Long userId) {
    List<Object[]> summary = reactRepository.summarizeByTarget(targetId, targetType);

    Map<String, Long> counts =
        summary.stream().collect(Collectors.toMap(arr -> (String) arr[0], arr -> (Long) arr[1]));

    String currentUserReact =
        reactRepository.findViewerReactNameForTarget(userId, targetId, targetType).orElse(null);

    return ReactSummaryDto.builder()
        .counts(counts)
        .total(counts.values().stream().mapToLong(Long::longValue).sum())
        .currentUserReact(currentUserReact)
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, ReactSummaryDto> getReactSummaries(
      List<Long> targetIds, Long viewerId, TargetType targetType) {
    if (targetIds == null || targetIds.isEmpty()) return Map.of();

    Map<Long, Map<String, Long>> allCountsMap = new HashMap<>();
    List<Object[]> summaryResults = reactRepository.summarizeByTargetList(targetIds, targetType);

    for (Object[] row : summaryResults) {
      Long targetId = (Long) row[0];
      String reactName = (String) row[1];
      Long count = (Long) row[2];
      allCountsMap.computeIfAbsent(targetId, k -> new HashMap<>()).put(reactName, count);
    }

    Map<Long, String> viewerReactsMap =
        reactRepository.findViewerReactsForTargetList(viewerId, targetIds, targetType).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));

    Map<Long, ReactSummaryDto> resultMap = new HashMap<>();
    for (Long targetId : targetIds) {
      Map<String, Long> counts = allCountsMap.getOrDefault(targetId, Map.of());
      long total = counts.values().stream().mapToLong(Long::longValue).sum();
      String currentUserReact = viewerReactsMap.get(targetId);

      resultMap.put(targetId, new ReactSummaryDto(counts, total, currentUserReact));
    }

    return resultMap;
  }
}
