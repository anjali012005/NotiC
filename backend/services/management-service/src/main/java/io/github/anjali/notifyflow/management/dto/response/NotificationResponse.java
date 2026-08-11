package io.github.anjali.notifyflow.management.dto.response;

import java.util.UUID;

import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
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

    private UUID notificationId;
    private NotificationDeliveryStatus status;
    private String message;
}
