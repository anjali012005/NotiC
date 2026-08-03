package io.github.anjali.notifyflow.management.service;

import java.util.List;
import java.util.UUID;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;

public interface NotificationProviderService {
    NotificationProviderResponse createProvider(CreateNotificationProviderRequest request);

    List<NotificationProviderResponse> getProviders(NotificationChannel channel, Boolean enabled);

    NotificationProviderResponse getProviderById(UUID id);

    NotificationProviderResponse updateProvider(UUID id, UpdateNotificationProviderRequest request);

    MessageResponse deleteProvider(UUID id);

    NotificationProviderResponse enableProvider(UUID id);

    NotificationProviderResponse disableProvider(UUID id);

    NotificationProviderResponse markAsDefault(UUID id);
}
