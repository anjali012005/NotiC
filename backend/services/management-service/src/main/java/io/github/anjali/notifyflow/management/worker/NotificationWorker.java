package io.github.anjali.notifyflow.management.worker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationRepository;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcher;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcherFactory;
import lombok.RequiredArgsConstructor;

/** Polls queued work and dispatches it after atomically claiming each notification. */
@Component
@RequiredArgsConstructor
public class NotificationWorker {

    private final NotificationRepository notificationRepository;
    private final NotificationProviderRepository providerRepository;
    private final NotificationTrackingService trackingService;
    private final NotificationDispatcherFactory dispatcherFactory;

    @Value("${notification.worker.batch-size:20}")
    private int batchSize;

    @Value("${notification.worker.processing-timeout-minutes:10}")
    private long processingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${notification.worker.fixed-delay-ms:5000}")
    public void processQueuedNotifications() {
        trackingService.recoverStuckNotifications(LocalDateTime.now().minusMinutes(processingTimeoutMinutes));
        List<UUID> notificationIds = notificationRepository.findIdsByStatus(
                NotificationDeliveryStatus.QUEUED, PageRequest.of(0, batchSize));

        for (UUID notificationId : notificationIds) {
            if (trackingService.claimQueuedNotification(notificationId)) {
                dispatchClaimedNotification(notificationId);
            }
        }
    }

    private void dispatchClaimedNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            return;
        }

        try {
            NotificationProvider provider = providerRepository.findById(notification.getProviderId())
                    .orElseThrow(() -> new IllegalStateException("Provider not found"));
            if (!provider.isEnabled()) {
                throw new IllegalStateException("Provider is not enabled: " + provider.getName());
            }

            NotificationDispatcher dispatcher = dispatcherFactory.resolveDispatcher(provider);
            dispatcher.dispatch(provider, notification.getRecipient(), notification.getSubject(), notification.getBody());
            trackingService.updateNotificationStatus(notificationId, NotificationDeliveryStatus.SENT);
            trackingService.setSentAt(notificationId);
        } catch (Exception exception) {
            trackingService.updateNotificationStatusWithFailure(notificationId, NotificationDeliveryStatus.FAILED,
                    "Provider dispatch failed: " + exception.getMessage());
        }
    }
}
