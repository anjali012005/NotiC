package io.github.anjali.notifyflow.management.worker;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.messaging.NotificationCreatedMessage;
import io.github.anjali.notifyflow.management.provider.ProviderProtectionService;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationRepository;
import io.github.anjali.notifyflow.management.retry.RetryPolicyProperties;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcher;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcherFactory;

/** Consumes notification work and dispatches it after atomically claiming each notification. */
@Component
public class NotificationWorker {

    private NotificationRepository notificationRepository;
    private NotificationProviderRepository providerRepository;
    private NotificationTrackingService trackingService;
    private NotificationDispatcherFactory dispatcherFactory;
    private RabbitTemplate rabbitTemplate;
    private RetryPolicyProperties retryPolicyProperties;
    private ProviderProtectionService providerProtectionService;

    public NotificationWorker() {
        this(null, null, null, null, null, null, null);
    }

    public NotificationWorker(NotificationRepository notificationRepository,
                              NotificationProviderRepository providerRepository,
                              NotificationTrackingService trackingService,
                              NotificationDispatcherFactory dispatcherFactory) {
        this(notificationRepository, providerRepository, trackingService, dispatcherFactory, null, null, null);
    }

    @Autowired
    public NotificationWorker(NotificationRepository notificationRepository,
                              NotificationProviderRepository providerRepository,
                              NotificationTrackingService trackingService,
                              NotificationDispatcherFactory dispatcherFactory,
                              RabbitTemplate rabbitTemplate,
                              RetryPolicyProperties retryPolicyProperties,
                              ProviderProtectionService providerProtectionService) {
        this.notificationRepository = notificationRepository;
        this.providerRepository = providerRepository;
        this.trackingService = trackingService;
        this.dispatcherFactory = dispatcherFactory;
        this.rabbitTemplate = rabbitTemplate;
        this.retryPolicyProperties = retryPolicyProperties;
        this.providerProtectionService = providerProtectionService;
    }

    @Value("${notification.worker.batch-size:20}")
    private int batchSize;

    @Value("${notification.worker.processing-timeout-minutes:10}")
    private long processingTimeoutMinutes;

    @Value("${notification.rabbitmq.retry-exchange:notifyflow.retry}")
    private String retryExchange;

    @Value("${notification.rabbitmq.dlx:notifyflow.dlx}")
    private String deadLetterExchange;

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consume(NotificationCreatedMessage message, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws java.io.IOException {
        processNotification(message.getNotificationId());
        channel.basicAck(deliveryTag, false);
    }

    @Scheduled(fixedDelayString = "${notification.rabbitmq.reconciliation-delay-ms:60000}")
    public void processQueuedNotifications() {
        trackingService.recoverStuckNotifications(LocalDateTime.now().minusMinutes(processingTimeoutMinutes));
    }

    private void processNotification(UUID notificationId) {
        if (trackingService.claimQueuedNotification(notificationId)) {
            dispatchClaimedNotification(notificationId);
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

            if (providerProtectionService != null) {
                providerProtectionService.beforeCall(provider);
            }

            NotificationDispatcher dispatcher = dispatcherFactory.resolveDispatcher(provider);
            dispatcher.dispatch(provider, notification.getRecipient(), notification.getSubject(), notification.getBody());
            if (providerProtectionService != null) {
                providerProtectionService.recordSuccess(provider);
            }
            trackingService.updateNotificationStatus(notificationId, NotificationDeliveryStatus.SENT);
            trackingService.setSentAt(notificationId);
        } catch (Exception exception) {
            if (providerProtectionService != null) {
                providerProtectionService.recordFailure(providerRepository.findById(notification.getProviderId()).orElse(null));
            }
            handleFailure(notificationId, notification, exception);
        }
    }

    private void handleFailure(UUID notificationId, Notification notification, Exception exception) {
        if (shouldRetry(notification, exception)) {
            int nextAttempt = (notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1;
            int maxAttempts = retryPolicyProperties != null ? retryPolicyProperties.getMaxAttempts() : 3;
            if (nextAttempt >= maxAttempts) {
                trackingService.updateNotificationStatusWithFailure(notificationId, NotificationDeliveryStatus.FAILED,
                        "Retry limit reached for provider failure: " + exception.getMessage());
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend(deadLetterExchange, "notification.dlq",
                            new NotificationCreatedMessage(notificationId, "NOTIFICATION_CREATED"));
                }
                return;
            }

            trackingService.incrementRetryCount(notificationId);
            if (rabbitTemplate != null) {
                String routingKey = buildRetryRoutingKey(nextAttempt);
                rabbitTemplate.convertAndSend(retryExchange, routingKey,
                        new NotificationCreatedMessage(notificationId, "NOTIFICATION_CREATED"));
            }
            return;
        }

        trackingService.updateNotificationStatusWithFailure(notificationId, NotificationDeliveryStatus.FAILED,
                "Provider dispatch failed: " + exception.getMessage());
    }

    private String buildRetryRoutingKey(int attemptNumber) {
        int delaySeconds = retryPolicyProperties != null
                ? retryPolicyProperties.getDelaySecondsForAttempt(attemptNumber)
                : 5;
        return "notification.retry." + formatDelay(delaySeconds);
    }

    private String formatDelay(int delaySeconds) {
        if (delaySeconds >= 60 && delaySeconds % 60 == 0) {
            long minutes = delaySeconds / 60L;
            return minutes + "m";
        }
        return delaySeconds + "s";
    }

    private boolean shouldRetry(Notification notification, Exception exception) {
        int currentRetryCount = notification.getRetryCount() == null ? 0 : notification.getRetryCount();
        int maxAttempts = retryPolicyProperties != null ? retryPolicyProperties.getMaxAttempts() : 3;
        if (currentRetryCount >= maxAttempts) {
            return false;
        }

        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        boolean retryable = message.contains("timeout")
                || message.contains("unavailable")
                || message.contains("temporar")
                || message.contains("503")
                || message.contains("network")
                || message.contains("connection")
                || message.contains("refused")
                || message.contains("rate limit")
                || message.contains("busy")
                || message.contains("service unavailable");

        if (!retryable) {
            return false;
        }

        return true;
    }
}
