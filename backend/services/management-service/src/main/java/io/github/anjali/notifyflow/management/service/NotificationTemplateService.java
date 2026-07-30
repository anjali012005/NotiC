package io.github.anjali.notifyflow.management.service;

import java.util.UUID;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import java.util.List;

import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateVersionResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;

public interface NotificationTemplateService {

    NotificationTemplateResponse createTemplate(CreateNotificationTemplateRequest request);

    NotificationTemplateResponse getTemplateById(UUID id);

    NotificationTemplateResponse getTemplateByKey(String templateKey);

    PageResponse<NotificationTemplateResponse> getTemplates(int page, int size, String sortBy, String sortDir,
            NotificationChannel channel, String tag);

    NotificationTemplateResponse updateTemplate(UUID id, UpdateNotificationTemplateRequest request);

    List<NotificationTemplateVersionResponse> getTemplateVersions(UUID id);

    NotificationTemplateVersionResponse getTemplateVersion(UUID id, Integer version);

    MessageResponse deleteTemplate(UUID id);
}