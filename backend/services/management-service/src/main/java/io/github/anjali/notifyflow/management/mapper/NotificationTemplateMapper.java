package io.github.anjali.notifyflow.management.mapper;

import java.util.HashSet;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;

@Component
public class NotificationTemplateMapper {

    public NotificationTemplate toEntity(CreateNotificationTemplateRequest request) {
        return NotificationTemplate.builder()
                .templateKey(request.getTemplateKey())
                .name(request.getName())
                .channel(request.getChannel())
                .subject(request.getSubject())
                .body(request.getBody())
                .tags(request.getTags() != null ? new HashSet<>(request.getTags()) : new HashSet<>())
                .build();
    }

    public NotificationTemplateResponse toResponse(NotificationTemplate template) {
        return toResponse(template, "Notification template retrieved successfully");
    }

    public NotificationTemplateResponse toResponse(NotificationTemplate template, String message) {
        return NotificationTemplateResponse.builder()
                .id(template.getId())
                .templateKey(template.getTemplateKey())
                .name(template.getName())
                .channel(template.getChannel())
                .subject(template.getSubject())
                .body(template.getBody())
                .tags(template.getTags())
                .createdAt(template.getCreatedAt())
                .message(message)
                .build();
    }

    public NotificationTemplate updateEntity(UpdateNotificationTemplateRequest request, NotificationTemplate existingTemplate) {
        existingTemplate.setName(request.getName());
        existingTemplate.setChannel(request.getChannel());
        existingTemplate.setSubject(request.getSubject());
        existingTemplate.setBody(request.getBody());
        if (request.getTags() != null) {
            existingTemplate.setTags(new HashSet<>(request.getTags()));
        }
        return existingTemplate;
    }
}
