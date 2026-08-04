package io.github.anjali.notifyflow.management.dto.response;

import java.util.UUID;

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
public class NotificationResponse {

    private UUID id;
    private String templateKey;
    private String recipient;
    private String subject;
    private String body;
    private String providerName;
    private String providerType;
    private String message;
}
