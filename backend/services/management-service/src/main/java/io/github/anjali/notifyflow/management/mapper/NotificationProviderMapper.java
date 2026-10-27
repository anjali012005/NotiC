package io.github.anjali.notifyflow.management.mapper;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.ProviderType;

@Component
public class NotificationProviderMapper {

    public NotificationProvider toEntity(CreateNotificationProviderRequest request) {
        return NotificationProvider.builder()
                .name(request.getName())
                .channel(request.getChannel())
                .providerType(request.getProviderType())
                .config(request.getConfig())
                .enabled(request.isEnabled())
                .isDefault(request.isDefault())
                .build();
    }

    public NotificationProviderResponse toResponse(NotificationProvider provider) {
        return toResponse(provider, "Notification provider retrieved successfully");
    }

    public NotificationProviderResponse toResponse(NotificationProvider provider, String message) {
        return NotificationProviderResponse.builder()
                .id(provider.getId())
                .name(provider.getName())
                .channel(provider.getChannel())
                .providerType(provider.getProviderType())
                .config(provider.getConfig())
                .configured(provider.getConfig() != null && !provider.getConfig().isBlank())
                .credentialsConfigured(provider.getProviderType() == ProviderType.SENDGRID
                        && !isBlank(System.getenv("SENDGRID_API_KEY")))
                .enabled(provider.isEnabled())
                .isDefault(provider.isDefault())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .message(message)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public void updateEntity(UpdateNotificationProviderRequest request, NotificationProvider provider) {
        provider.setName(request.getName());
        provider.setChannel(request.getChannel());
        provider.setProviderType(request.getProviderType());
        provider.setConfig(request.getConfig());
        if (request.getEnabled() != null) {
            provider.setEnabled(request.getEnabled());
        }
        if (request.getIsDefault() != null) {
            provider.setDefault(request.getIsDefault());
        }
    }
}
