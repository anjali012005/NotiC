package io.github.anjali.notifyflow.management.service;

import io.github.anjali.notifyflow.management.dto.request.SendNotificationRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationResponse;

public interface NotificationService {

    NotificationResponse sendNotification(SendNotificationRequest request);
}
