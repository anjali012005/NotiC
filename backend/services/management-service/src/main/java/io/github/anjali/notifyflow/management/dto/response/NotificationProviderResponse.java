package io.github.anjali.notifyflow.management.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
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
public class NotificationProviderResponse {
    private UUID id;
    private String name;
    private NotificationChannel channel;
    private ProviderType providerType;
    /** Retained for source compatibility but deliberately omitted from JSON responses. */
    @JsonIgnore
    private String config;
    private boolean configured;
    private boolean enabled;

    @JsonProperty("isDefault")
    private boolean isDefault;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String message;
}
