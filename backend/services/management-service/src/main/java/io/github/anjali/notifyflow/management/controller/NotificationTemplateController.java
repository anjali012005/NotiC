package io.github.anjali.notifyflow.management.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.anjali.notifyflow.management.dto.request.CreateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.CreateTemplateVersionRequest;
import io.github.anjali.notifyflow.management.dto.request.RenderTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.UpdateNotificationTemplateRequest;
import io.github.anjali.notifyflow.management.dto.response.MessageResponse;
import io.github.anjali.notifyflow.management.dto.response.NotificationTemplateResponse;
import io.github.anjali.notifyflow.management.dto.response.PageResponse;
import io.github.anjali.notifyflow.management.dto.response.RenderTemplateResponse;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.service.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService notificationTemplateService;

    @PostMapping
    public ResponseEntity<NotificationTemplateResponse> createTemplate(@Valid @RequestBody CreateNotificationTemplateRequest request) {
        NotificationTemplateResponse response = notificationTemplateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<NotificationTemplateResponse>> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) String tag) {
        PageResponse<NotificationTemplateResponse> response = notificationTemplateService.getTemplates(page, size, sortBy, sortDir, channel, tag);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationTemplateResponse> getTemplateById(@PathVariable UUID id) {
        NotificationTemplateResponse response = notificationTemplateService.getTemplateById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/key/{templateKey}")
    public ResponseEntity<NotificationTemplateResponse> getTemplateByKey(@PathVariable String templateKey) {
        NotificationTemplateResponse response = notificationTemplateService.getTemplateByKey(templateKey);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(@PathVariable UUID id,
            @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        NotificationTemplateResponse response = notificationTemplateService.updateTemplate(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteTemplate(@PathVariable UUID id) {
        MessageResponse response = notificationTemplateService.deleteTemplate(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<NotificationTemplateResponse> createVersion(@PathVariable UUID id,
            @Valid @RequestBody CreateTemplateVersionRequest request) {
        NotificationTemplateResponse response = notificationTemplateService.createVersion(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/variables")
    public ResponseEntity<List<String>> getVariables(@PathVariable UUID id) {
        List<String> response = notificationTemplateService.getVariables(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/render")
    public ResponseEntity<RenderTemplateResponse> renderTemplate(@PathVariable UUID id,
            @Valid @RequestBody RenderTemplateRequest request) {
        RenderTemplateResponse response = notificationTemplateService.renderTemplate(id, request);
        return ResponseEntity.ok(response);
    }
}
