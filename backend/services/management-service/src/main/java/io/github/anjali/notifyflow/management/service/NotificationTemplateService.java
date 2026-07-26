package io.github.anjali.notifyflow.management.service;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;

public interface NotificationTemplateService {

    NotificationTemplateResponse createTemplate(CreateNotificationTemplateRequest request);
}