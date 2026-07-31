package io.github.anjali.notifyflow.management.service;

import java.util.List;
import java.util.UUID;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.CreateTemplateVersionRequest;
import io.github.anjali.notifyflow.management.dto.request.RenderTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.dto.response.RenderTemplateResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;

public interface NotificationTemplateService {

    NotificationTemplateResponse createTemplate(CreateNotificationTemplateRequest request);

    NotificationTemplateResponse getTemplateById(UUID id);

    NotificationTemplateResponse getTemplateByKey(String templateKey);

    PageResponse<NotificationTemplateResponse> getTemplates(int page, int size, String sortBy, String sortDir,
            NotificationChannel channel, String tag);

    NotificationTemplateResponse updateTemplate(UUID id, UpdateNotificationTemplateRequest request);

    MessageResponse deleteTemplate(UUID id);

    List<String> getVariables(UUID templateId);

    RenderTemplateResponse renderTemplate(UUID templateId, RenderTemplateRequest request);

    NotificationTemplateResponse createVersion(UUID templateId, CreateTemplateVersionRequest request);

    java.util.List<io.github.anjali.notifyflow.management.dto.response.NotificationTemplateVersionResponse> getVersions(UUID templateId);

    io.github.anjali.notifyflow.management.dto.response.NotificationTemplateVersionResponse getVersion(UUID templateId, Integer versionNumber);
}