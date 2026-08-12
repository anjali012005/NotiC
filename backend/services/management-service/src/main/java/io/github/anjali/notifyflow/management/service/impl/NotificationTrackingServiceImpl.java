package io.github.anjali.notifyflow.management.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.dto.response.NotificationDetailsResponse;
import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.entity.NotificationTemplate;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationMapper;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationRepository;
import io.github.anjali.notifyflow.management.repository.NotificationTemplateRepository;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcher;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcherFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationTrackingServiceImpl implements NotificationTrackingService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationProviderRepository providerRepository;
    private final NotificationDispatcherFactory dispatcherFactory;
    private final NotificationMapper notificationMapper;

    @Value("${notification.max-retry-count:3}")
    private int maxRetryCount;

    @Override
    @Transactional
    public Notification createNotification(UUID templateId, UUID templateVersionId, String templateKey,
                                            String recipient, NotificationChannel channel,
                                            UUID providerId, String providerName,
                                            String subject, String body) {
        return notificationRepository.save(Notification.builder()
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
        return notificationRepository.save(notification);
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
        return notificationRepository.findByFilters(status, channel, recipient, providerName, pageable)
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

        NotificationTemplate template = templateRepository.findById(notification.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        NotificationProvider provider = providerRepository.findById(notification.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (!provider.isEnabled()) {
            updateNotificationStatusWithFailure(notificationId, NotificationDeliveryStatus.FAILED,
                    "Provider is not enabled");
            return false;
        }

        incrementRetryCount(notificationId);
        updateNotificationStatus(notificationId, NotificationDeliveryStatus.PROCESSING);

        try {
            NotificationDispatcher dispatcher = dispatcherFactory.resolveDispatcher(provider);
            dispatcher.dispatch(provider, notification.getRecipient(), notification.getSubject(), notification.getBody());

            updateNotificationStatus(notificationId, NotificationDeliveryStatus.SENT);
            setSentAt(notificationId);
            return true;
        } catch (Exception e) {
            updateNotificationStatusWithFailure(notificationId, NotificationDeliveryStatus.FAILED,
                    "Retry failed: " + e.getMessage());
            return false;
        }
    }

    private Notification findByIdOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
    }
}
