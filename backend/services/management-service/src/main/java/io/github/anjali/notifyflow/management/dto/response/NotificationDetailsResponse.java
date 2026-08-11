package io.github.anjali.notifyflow.management.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDetailsResponse {

    private UUID id;
    private UUID templateId;
    private UUID templateVersionId;
    private String templateKey;
    private String recipient;
    private NotificationChannel channel;
    private UUID providerId;
    private String providerName;
    private String subject;
    private String body;
    private NotificationDeliveryStatus status;
    private Integer retryCount;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime sentAt;
}