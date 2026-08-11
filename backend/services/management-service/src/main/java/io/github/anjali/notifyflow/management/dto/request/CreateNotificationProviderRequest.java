package io.github.anjali.notifyflow.management.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateNotificationProviderRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Channel is required")
    private NotificationChannel channel;

    @NotNull(message = "Provider type is required")
    private ProviderType providerType;

    @NotBlank(message = "Config is required")
    private String config;

    private boolean enabled;

    @JsonProperty("isDefault")
    private boolean isDefault;
}
