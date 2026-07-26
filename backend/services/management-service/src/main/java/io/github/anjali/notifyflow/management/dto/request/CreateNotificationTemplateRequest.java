package io.github.anjali.notifyflow.management.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateNotificationTemplateRequest {

    @NotBlank(message = "Template key is required")
    private String templateKey;

    @NotBlank(message = "Title is required")
    private String title;

    private String subject;

    @NotBlank(message = "Body is required")
    private String body;
}