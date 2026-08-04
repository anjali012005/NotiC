package io.github.anjali.notifyflow.management.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.anjali.notifyflow.management.dto.request.SendNotificationRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationResponse;
import io.github.anjali.notifyflow.management.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.ok(notificationService.sendNotification(request));
    }
}
