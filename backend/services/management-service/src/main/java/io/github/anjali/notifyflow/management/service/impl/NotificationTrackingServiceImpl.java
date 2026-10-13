package io.github.anjali.notifyflow.management.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.response.NotificationDetailsResponse;
import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.entity.OutboxEvent;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.enums.OutboxEventStatus;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationMapper;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationRepository;
import io.github.anjali.notifyflow.management.repository.OutboxEventRepository;
import io.github.anjali.notifyflow.management.security.TenantAuthorizationService;
import io.github.anjali.notifyflow.management.security.TenantContext;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import io.github.anjali.notifyflow.management.observability.NotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationTrackingServiceImpl implements NotificationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(NotificationTrackingServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final NotificationProviderRepository providerRepository;
    private final NotificationMapper notificationMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private NotificationMetrics notificationMetrics = new NotificationMetrics(new SimpleMeterRegistry());

    @Value("${notification.max-retry-count:3}")
    private int maxRetryCount;

    @Override
    @Transactional
    public Notification createNotification(UUID templateId, UUID templateVersionId, String templateKey,
                                            String recipient, NotificationChannel channel,
                                            UUID providerId, String providerName,
                                            String subject, String body) {
        return notificationRepository.save(Notification.builder()
                .tenantId(currentTenantId())
                .templateId(templateId)
                .templateVersionId(templateVersionId)
                .templateKey(templateKey)
                .recipient(recipient)
                .channel(channel)
                .providerId(providerId)
                .providerName(providerName)
                .subject(subject)
                .body(body)
                .status(NotificationDeliveryStatus.QUEUED)
                .retryCount(0)
                .build());
    }

    @Override
    @Transactional
    public Notification updateNotificationStatus(UUID notificationId, NotificationDeliveryStatus status) {
        Notification notification = findByIdOrThrow(notificationId);
        notification.setStatus(status);
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public Notification updateNotificationStatusWithFailure(UUID notificationId, NotificationDeliveryStatus status, String failureReason) {
        Notification notification = findByIdOrThrow(notificationId);
        notification.setStatus(status);
        notification.setFailureReason(failureReason);
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public Notification incrementRetryCount(UUID notificationId) {
        Notification notification = findByIdOrThrow(notificationId);
        notification.setRetryCount(notification.getRetryCount() + 1);
        notificationRepository.save(notification);
        log.info("notification.retry.scheduled notificationId={} retryCount={} correlationId={}",
                notificationId, notification.getRetryCount(), io.github.anjali.notifyflow.management.observability.CorrelationContext.currentOrCreate());
        return notification;
    }

    @Override
    @Transactional
    public Notification setSentAt(UUID notificationId) {
        Notification notification = findByIdOrThrow(notificationId);
        notification.setSentAt(LocalDateTime.now());
        notification.setFailureReason(null);
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationDetailsResponse getNotificationById(UUID id) {
        Notification notification = findByIdOrThrow(id);
        return notificationMapper.toDetailsResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDetailsResponse> getNotifications(NotificationDeliveryStatus status, NotificationChannel channel,
                                                               String recipient, String providerName, Pageable pageable) {
        return notificationRepository.findByTenantIdAndFilters(currentTenantId(), status, channel, recipient, providerName, pageable)
                .map(notificationMapper::toDetailsResponse);
    }

    @Override
    @Transactional
    public boolean retryNotification(UUID notificationId) {
        Notification notification = findByIdOrThrow(notificationId);

        if (notification.getStatus() != NotificationDeliveryStatus.FAILED) {
            return false;
        }

        if (notification.getRetryCount() >= maxRetryCount) {
            return false;
        }

        NotificationProvider provider = providerRepository.findByTenantIdAndId(
                currentTenantId(), notification.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (!provider.isEnabled()) {
            updateNotificationStatusWithFailure(notificationId, NotificationDeliveryStatus.FAILED,
                    "Provider is not enabled");
            return false;
        }

        incrementRetryCount(notificationId);
        notification.setStatus(NotificationDeliveryStatus.QUEUED);
        notification.setFailureReason(null);
        notificationRepository.save(notification);
        outboxEventRepository.save(createNotificationCreatedEvent(notificationId));
        return true;
    }

    private OutboxEvent createNotificationCreatedEvent(UUID notificationId) {
        return OutboxEvent.builder()
                .tenantId(currentTenantId())
                .eventType("NOTIFICATION_CREATED")
                .aggregateType("NOTIFICATION")
                .aggregateId(notificationId)
                .payload("{\"notificationId\":\"" + notificationId + "\"}")
                .status(OutboxEventStatus.PENDING)
                .attemptCount(0)
                .build();
    }

    @Override
    @Transactional
    public boolean claimQueuedNotification(UUID notificationId) {
        return notificationRepository.claimQueuedNotification(
                notificationId, NotificationDeliveryStatus.QUEUED, NotificationDeliveryStatus.PROCESSING) == 1;
    }

    @Override
    @Transactional
    public void recoverStuckNotifications(LocalDateTime cutoff) {
        String reason = "Processing lease expired; notification re-queued";
        notificationRepository.failExhaustedStuckNotifications(
                NotificationDeliveryStatus.PROCESSING, NotificationDeliveryStatus.FAILED,
                cutoff, maxRetryCount, "Processing lease expired after maximum retries");
        notificationRepository.requeueStuckNotifications(
                NotificationDeliveryStatus.PROCESSING, NotificationDeliveryStatus.QUEUED,
                cutoff, maxRetryCount, reason);
    }

    private Notification findByIdOrThrow(UUID id) {
        Notification notification = notificationRepository.findByTenantIdAndId(currentTenantId(), id)
                .orElseGet(() -> notificationRepository.findById(id).orElse(null));
        if (notification == null) {
            throw new ResourceNotFoundException("Notification not found with id: " + id);
        }
        tenantAuthorizationService.requireTenantAccess(notification.getTenantId());
        return notification;
    }

    private UUID currentTenantId() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return tenantId == null ? UUID.fromString("00000000-0000-0000-0000-000000000000") : tenantId;
    }
}
