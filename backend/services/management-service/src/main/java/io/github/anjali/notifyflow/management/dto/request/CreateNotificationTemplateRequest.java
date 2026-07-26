package io.github.anjali.notifyflow.management.dto.request;

import java.util.Set;

import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateNotificationTemplateRequest {

    @NotBlank(message = "Template key is required")
    @Size(max = 100, message = "Template key must not exceed 100 characters")
    private String templateKey;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Channel is required")
    private NotificationChannel channel;

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject must not exceed 255 characters")
    private String subject;

    @NotBlank(message = "Body is required")
    private String body;

    private Set<String> tags;
}