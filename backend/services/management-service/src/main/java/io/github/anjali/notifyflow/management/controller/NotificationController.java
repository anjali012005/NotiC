package io.github.anjali.notifyflow.management.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.anjali.notifyflow.management.dto.request.SendNotificationRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationDetailsResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.service.NotificationService;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationTrackingService trackingService;

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(notificationService.sendNotification(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDetailsResponse> getNotificationById(@PathVariable UUID id) {
        return ResponseEntity.ok(trackingService.getNotificationById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationDetailsResponse>> getNotifications(
            @RequestParam(required = false) NotificationDeliveryStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String providerName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Sort sort = Sort.by(sortDirection.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<NotificationDetailsResponse> notifications = trackingService.getNotifications(
                status, channel, recipient, providerName, pageable);

        return ResponseEntity.ok(PageResponse.<NotificationDetailsResponse>builder()
                .content(notifications.getContent())
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalPages(notifications.getTotalPages())
                .totalElements(notifications.getTotalElements())
                .last(notifications.isLast())
                .build());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<MessageResponse> retryNotification(@PathVariable UUID id) {
        boolean success = trackingService.retryNotification(id);
        if (success) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(MessageResponse.builder()
                    .message("Notification retry queued successfully")
                    .build());
        } else {
            return ResponseEntity.badRequest().body(MessageResponse.builder()
                    .message("Notification retry failed. Either max retries exceeded or notification not in FAILED status")
                    .build());
        }
    }
}
