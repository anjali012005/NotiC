package io.github.anjali.notifyflow.management.mapper;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.dto.response.NotificationDetailsResponse;
import io.github.anjali.notifyflow.management.entity.Notification;

@Component
public class NotificationMapper {

    public NotificationDetailsResponse toDetailsResponse(Notification notification) {
        return NotificationDetailsResponse.builder()
                .id(notification.getId())
                .templateId(notification.getTemplateId())
                .templateVersionId(notification.getTemplateVersionId())
                .templateKey(notification.getTemplateKey())
                .recipient(notification.getRecipient())
                .channel(notification.getChannel())
                .providerId(notification.getProviderId())
                .providerName(notification.getProviderName())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .retryCount(notification.getRetryCount())
                .failureReason(notification.getFailureReason())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .sentAt(notification.getSentAt())
                .build();
    }
}