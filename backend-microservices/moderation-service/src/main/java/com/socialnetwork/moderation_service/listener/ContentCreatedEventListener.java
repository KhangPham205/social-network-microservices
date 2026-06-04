package com.socialnetwork.moderation_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import events.ContentCreatedEvent;
import events.ModerationActionEvent;
import com.socialnetwork.moderation_service.client.AiServiceClient;
import com.socialnetwork.moderation_service.dto.AiModerationRequest;
import com.socialnetwork.moderation_service.dto.AiModerationResponse;
import com.socialnetwork.moderation_service.model.ModerationLog;
import com.socialnetwork.moderation_service.repository.ModerationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vo.TargetType;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentCreatedEventListener {

    private final ObjectMapper objectMapper;
    private final AiServiceClient aiServiceClient;
    private final ModerationLogRepository moderationLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "content-created-topic", groupId = "moderation-service-group")
    @Transactional
    public void handleContentCreated(String messagePayload) {
        try {
            log.info("Received raw content payload for moderation: {}", messagePayload);
            
            // CRITICAL RULE: Parse raw String message
            ContentCreatedEvent event = objectMapper.readValue(messagePayload, ContentCreatedEvent.class);

            // 1. Call AI Service
            AiModerationRequest aiRequest = AiModerationRequest.builder()
                    .text(event.getContent())
                    .media(event.getMedia())
                    .build();
            
            AiModerationResponse aiResponse = aiServiceClient.checkToxicity(aiRequest);
            log.info("AI evaluation for {} {}: isToxic={}, score={}", 
                     event.getTargetType(), event.getTargetId(), aiResponse.isToxic(), aiResponse.getConfidenceScore());

            // 2. If toxic, log & publish BLOCK action
            if (aiResponse.isToxic()) {
                ModerationLog logEntry = ModerationLog.builder()
                        .targetId(String.valueOf(event.getTargetId()))
                        .targetType(TargetType.valueOf(event.getTargetType().toUpperCase()))
                        .action("AUTO_BAN")
                        .reason("Toxic content detected by AI Service (score: " + aiResponse.getConfidenceScore() + ")")
                        .actorId(null) // Automated action
                        .build();
                moderationLogRepository.save(logEntry);

                ModerationActionEvent actionEvent = ModerationActionEvent.builder()
                        .targetId(String.valueOf(event.getTargetId()))
                        .targetType(event.getTargetType())
                        .action("BLOCK")
                        .build();
                
                String actionPayload = objectMapper.writeValueAsString(actionEvent);
                kafkaTemplate.send("moderation-actions", actionPayload);
                
                log.info("Published BLOCK action for {} {}", event.getTargetType(), event.getTargetId());
            }

        } catch (Exception e) {
            log.error("Failed to process content moderation. Payload: {}", messagePayload, e);
        }
    }
}
