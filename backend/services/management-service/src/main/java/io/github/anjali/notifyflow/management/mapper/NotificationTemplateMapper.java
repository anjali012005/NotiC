package io.github.anjali.notifyflow.management.mapper;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
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
                .tags(request.getTags())
                .build();
    }

    public NotificationTemplateResponse toResponse(NotificationTemplate template) {
        return NotificationTemplateResponse.builder()
                .id(template.getId())
                .message("Notification template created successfully")
                .build();
    }
}
