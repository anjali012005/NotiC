package io.github.anjali.notifyflow.management.messaging;

import java.util.UUID;

public interface NotificationEventPublisher {

    void publishNotificationCreated(UUID notificationId);
}