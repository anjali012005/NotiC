package io.github.anjali.notifyflow.management.mapper;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateVersionResponse;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;

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

    public NotificationTemplate updateEntity(UpdateNotificationTemplateRequest request,
                                             NotificationTemplate existingTemplate) {
        existingTemplate.setName(request.getName());
        existingTemplate.setChannel(request.getChannel());
        existingTemplate.setSubject(request.getSubject());
        existingTemplate.setBody(request.getBody());

        if (request.getTags() != null) {
            existingTemplate.setTags(new HashSet<>(request.getTags()));
        }

        return existingTemplate;
    }

    public NotificationTemplateVersionResponse toVersionResponse(NotificationTemplateVersion version) {
        return NotificationTemplateVersionResponse.builder()
                .id(version.getId())
                .version(version.getVersion())
                .versionNumber(version.getVersionNumber())
                .name(version.getName())
                .channel(version.getChannel())
                .subject(version.getSubject())
                .body(version.getBody())
                .tags(version.getTags())
                .active(version.isActive())
                .createdAt(version.getCreatedAt())
                .updatedAt(version.getUpdatedAt())
                .variables(
                        version.getVariables() == null
                                ? List.of()
                                : version.getVariables().stream()
                                        .map(v -> v.getVariableName())
                                        .toList())
                .build();
    }

    public NotificationTemplateVersion toVersionEntity(
            NotificationTemplate template,
            Integer versionNumber,
            NotificationTemplate existingTemplate) {

        return NotificationTemplateVersion.builder()
                .template(template)
                .version(versionNumber)
                .versionNumber(versionNumber)
                .name(existingTemplate.getName())
                .channel(existingTemplate.getChannel())
                .subject(existingTemplate.getSubject())
                .body(existingTemplate.getBody())
                .tags(existingTemplate.getTags() != null
                        ? new HashSet<>(existingTemplate.getTags())
                        : new HashSet<>())
                .active(true)
                .build();
    }
}