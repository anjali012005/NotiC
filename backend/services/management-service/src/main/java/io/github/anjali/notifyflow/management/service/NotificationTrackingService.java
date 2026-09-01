package io.github.anjali.notifyflow.management.service;

import java.util.UUID;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.github.anjali.notifyflow.management.dto.response.NotificationDetailsResponse;
import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;

public interface NotificationTrackingService {

    Notification createNotification(UUID templateId, UUID templateVersionId, String templateKey,
                                     String recipient, NotificationChannel channel,
                                     UUID providerId, String providerName,
                                     String subject, String body);

    Notification updateNotificationStatus(UUID notificationId, NotificationDeliveryStatus status);

    Notification updateNotificationStatusWithFailure(UUID notificationId, NotificationDeliveryStatus status, String failureReason);

    Notification incrementRetryCount(UUID notificationId);

    Notification setSentAt(UUID notificationId);

    NotificationDetailsResponse getNotificationById(UUID id);

    Page<NotificationDetailsResponse> getNotifications(NotificationDeliveryStatus status, NotificationChannel channel,
                                                        String recipient, String providerName, Pageable pageable);

    boolean retryNotification(UUID notificationId);

    boolean claimQueuedNotification(UUID notificationId);

    void recoverStuckNotifications(LocalDateTime cutoff);
}
