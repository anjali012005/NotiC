package io.github.anjali.notifyflow.management.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationProviderRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationProviderResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.service.NotificationProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class NotificationProviderController {

    private final NotificationProviderService notificationProviderService;

    @PostMapping
    public ResponseEntity<NotificationProviderResponse> createProvider(@Valid @RequestBody CreateNotificationProviderRequest request) {
        NotificationProviderResponse response = notificationProviderService.createProvider(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<NotificationProviderResponse>> getProviders(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) Boolean enabled) {
        List<NotificationProviderResponse> response = notificationProviderService.getProviders(channel, enabled);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationProviderResponse> getProviderById(@PathVariable UUID id) {
        NotificationProviderResponse response = notificationProviderService.getProviderById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationProviderResponse> updateProvider(@PathVariable UUID id,
            @Valid @RequestBody UpdateNotificationProviderRequest request) {
        NotificationProviderResponse response = notificationProviderService.updateProvider(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteProvider(@PathVariable UUID id) {
        MessageResponse response = notificationProviderService.deleteProvider(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<NotificationProviderResponse> enableProvider(@PathVariable UUID id) {
        NotificationProviderResponse response = notificationProviderService.enableProvider(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<NotificationProviderResponse> disableProvider(@PathVariable UUID id) {
        NotificationProviderResponse response = notificationProviderService.disableProvider(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<NotificationProviderResponse> markAsDefault(@PathVariable UUID id) {
        NotificationProviderResponse response = notificationProviderService.markAsDefault(id);
        return ResponseEntity.ok(response);
    }
}
