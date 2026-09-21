package io.github.anjali.notifyflow.management.messaging;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCreatedMessage {

    private UUID notificationId;
    private String eventType;
}