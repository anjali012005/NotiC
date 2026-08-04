package io.github.anjali.notifyflow.management.dto.request;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
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
public class SendNotificationRequest {

    @NotBlank(message = "Template key is required")
    private String templateKey;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @Builder.Default
    private Map<String, String> variables = new LinkedHashMap<>();
}
