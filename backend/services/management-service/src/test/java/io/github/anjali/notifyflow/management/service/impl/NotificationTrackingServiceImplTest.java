package io.github.anjali.notifyflow.management.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.ResourceNotFoundException;
import io.github.anjali.notifyflow.management.mapper.NotificationMapper;
import io.github.anjali.notifyflow.management.messaging.NotificationEventPublisher;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationRepository;
import io.github.anjali.notifyflow.management.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class NotificationTrackingServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

        @Mock
        private OutboxEventRepository outboxEventRepository;

    @Mock
    private NotificationProviderRepository providerRepository;

    @Mock
    private NotificationMapper notificationMapper;

        @Mock
        private NotificationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationTrackingServiceImpl trackingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(trackingService, "maxRetryCount", 3);
    }

    @Test
    void createNotificationSavesWithQueuedStatus() {
        UUID templateId = UUID.randomUUID();
        UUID templateVersionId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .templateId(templateId)
                .templateVersionId(templateVersionId)
                .templateKey("WELCOME_EMAIL")
                .recipient("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .providerId(providerId)
                .providerName("SendGrid")
                .subject("Hello")
                .body("World")
                .status(NotificationDeliveryStatus.QUEUED)
                .retryCount(0)
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = trackingService.createNotification(
                templateId, templateVersionId, "WELCOME_EMAIL",
                "user@example.com", NotificationChannel.EMAIL,
                providerId, "SendGrid", "Hello", "World");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(NotificationDeliveryStatus.QUEUED);
        assertThat(result.getRetryCount()).isEqualTo(0);
    }

    @Test
    void updateNotificationStatusChangesStatus() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .status(NotificationDeliveryStatus.QUEUED)
                .retryCount(0)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = trackingService.updateNotificationStatus(notificationId, NotificationDeliveryStatus.PROCESSING);

        assertThat(result.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
    }

    @Test
    void updateNotificationStatusWithFailureSetsFailureReason() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .status(NotificationDeliveryStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = trackingService.updateNotificationStatusWithFailure(
                notificationId, NotificationDeliveryStatus.FAILED, "Connection timeout");

        assertThat(result.getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("Connection timeout");
    }

    @Test
    void incrementRetryCountIncrementsCount() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .status(NotificationDeliveryStatus.FAILED)
                .retryCount(1)
                .failureReason("Previous attempt timed out")
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = trackingService.incrementRetryCount(notificationId);

        assertThat(result.getRetryCount()).isEqualTo(2);
    }

    @Test
    void recoverStuckNotificationsFailsExhaustedWorkAndRequeuesOnlyWorkBelowTheRetryLimit() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        trackingService.recoverStuckNotifications(cutoff);

        verify(notificationRepository).failExhaustedStuckNotifications(
                NotificationDeliveryStatus.PROCESSING, NotificationDeliveryStatus.FAILED,
                cutoff, 3, "Processing lease expired after maximum retries");
        verify(notificationRepository).requeueStuckNotifications(
                NotificationDeliveryStatus.PROCESSING, NotificationDeliveryStatus.QUEUED,
                cutoff, 3, "Processing lease expired; notification re-queued");
    }

    @Test
    void getNotificationByIdReturnsDetails() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(notificationId)
                .templateKey("WELCOME_EMAIL")
                .recipient("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .providerName("SendGrid")
                .status(NotificationDeliveryStatus.SENT)
                .retryCount(0)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationMapper.toDetailsResponse(notification)).thenReturn(any());

        trackingService.getNotificationById(notificationId);

        verify(notificationRepository).findById(notificationId);
    }

    @Test
    void getNotificationByIdThrowsWhenNotFound() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trackingService.getNotificationById(notificationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void retryNotificationSucceedsWhenUnderMaxRetries() {
        UUID notificationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .templateId(templateId)
                .templateVersionId(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .recipient("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .providerId(providerId)
                .providerName("SendGrid")
                .subject("Hello")
                .body("World")
                .status(NotificationDeliveryStatus.FAILED)
                .retryCount(1)
                .build();

        NotificationProvider provider = NotificationProvider.builder()
                .id(providerId)
                .enabled(true)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = trackingService.retryNotification(notificationId);

        assertThat(result).isTrue();
    }

    @Test
    void retryNotificationFailsWhenMaxRetriesExceeded() {
        UUID notificationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .templateId(templateId)
                .templateVersionId(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .recipient("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .providerId(providerId)
                .providerName("SendGrid")
                .subject("Hello")
                .body("World")
                .status(NotificationDeliveryStatus.FAILED)
                .retryCount(3)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        boolean result = trackingService.retryNotification(notificationId);

        assertThat(result).isFalse();
    }

    @Test
    void retryNotificationFailsWhenNotFailed() {
        UUID notificationId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .status(NotificationDeliveryStatus.SENT)
                .retryCount(0)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        boolean result = trackingService.retryNotification(notificationId);

        assertThat(result).isFalse();
    }

    @Test
    void retryNotificationFailsWhenProviderDisabled() {
        UUID notificationId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        Notification notification = Notification.builder()
                .id(notificationId)
                .templateId(templateId)
                .templateVersionId(UUID.randomUUID())
                .templateKey("WELCOME_EMAIL")
                .recipient("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .providerId(providerId)
                .providerName("SendGrid")
                .subject("Hello")
                .body("World")
                .status(NotificationDeliveryStatus.FAILED)
                .retryCount(1)
                .build();

        NotificationProvider provider = NotificationProvider.builder()
                .id(providerId)
                .enabled(false)
                .build();

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(providerRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        boolean result = trackingService.retryNotification(notificationId);

        assertThat(result).isFalse();
    }
}
