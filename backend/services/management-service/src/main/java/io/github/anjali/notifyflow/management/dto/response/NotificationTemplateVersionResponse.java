package io.github.anjali.notifyflow.management.dto.response;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import io.github.anjali.notifyflow.management.enums.NotificationChannel;
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
public class NotificationTemplateVersionResponse {
    private UUID id;
    private Integer version;
    private String name;
    private NotificationChannel channel;
    private String subject;
    private String body;
    private Set<String> tags;
    private LocalDateTime createdAt;
}
