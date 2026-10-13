package io.github.anjali.notifyflow.management.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import io.github.anjali.notifyflow.management.dto.request.RenderTemplateRequest;
import io.github.anjali.notifyflow.management.dto.request.SendNotificationRequest;
import io.github.anjali.notifyflow.management.dto.response.NotificationResponse;
import io.github.anjali.notifyflow.management.dto.response.RenderTemplateResponse;
import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.entity.NotificationTemplateVersion;
import io.github.anjali.notifyflow.management.entity.OutboxEvent;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.enums.OutboxEventStatus;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateVersionRepository;
import io.github.anjali.notifyflow.management.repository.OutboxEventRepository;
import io.github.anjali.notifyflow.management.service.NotificationService;
import io.github.anjali.notifyflow.management.service.NotificationTemplateService;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateVersionRepository versionRepository;
    private final NotificationProviderRepository providerRepository;
    private final NotificationTrackingService trackingService;
    private final NotificationTemplateService templateService;
    private final OutboxEventRepository outboxEventRepository;
    private io.github.anjali.notifyflow.management.observability.NotificationMetrics notificationMetrics =
            new io.github.anjali.notifyflow.management.observability.NotificationMetrics(new SimpleMeterRegistry());

    @Override
    @Transactional
    public NotificationResponse sendNotification(SendNotificationRequest request) {
        NotificationTemplate template = templateRepository.findByTemplateKey(request.getTemplateKey())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with key: " + request.getTemplateKey()));

        NotificationTemplateVersion activeVersion = versionRepository.findByTemplate_IdAndActiveTrue(template.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No active version found for template: " + template.getTemplateKey()));

        RenderTemplateRequest renderRequest = new RenderTemplateRequest();
        renderRequest.setVariables(request.getVariables());
        RenderTemplateResponse renderedTemplate = templateService.renderTemplate(template.getId(), renderRequest);

        NotificationProvider provider = providerRepository.findByChannelAndIsDefaultTrue(template.getChannel())
                .orElseThrow(() -> new ResourceNotFoundException("Default provider not found for channel: " + template.getChannel()));

        if (!provider.isEnabled()) {
            throw new ResourceNotFoundException("Provider is not enabled: " + provider.getName());
        }

        Notification notification = trackingService.createNotification(
                template.getId(),
                activeVersion.getId(),
                template.getTemplateKey(),
                request.getRecipient(),
                template.getChannel(),
                provider.getId(),
                provider.getName(),
                renderedTemplate.getSubject(),
                renderedTemplate.getBody()
        );
        outboxEventRepository.save(createNotificationCreatedEvent(notification.getId()));
        notificationMetrics.recordNotificationAccepted(template.getChannel().name());
        log.info("notification.accepted notificationId={} channel={} templateKey={} correlationId={}",
                notification.getId(), template.getChannel(), template.getTemplateKey(),
                io.github.anjali.notifyflow.management.observability.CorrelationContext.currentOrCreate());

        return NotificationResponse.builder()
                .id(notification.getId())
                .notificationId(notification.getId())
                .status(NotificationDeliveryStatus.QUEUED)
                .message("Notification accepted for processing")
                .build();
    }

    private OutboxEvent createNotificationCreatedEvent(UUID notificationId) {
        return OutboxEvent.builder()
                .eventType("NOTIFICATION_CREATED")
                .aggregateType("NOTIFICATION")
                .aggregateId(notificationId)
                .payload("{\"notificationId\":\"" + notificationId + "\"}")
                .status(OutboxEventStatus.PENDING)
                .attemptCount(0)
                .build();
        }

}
