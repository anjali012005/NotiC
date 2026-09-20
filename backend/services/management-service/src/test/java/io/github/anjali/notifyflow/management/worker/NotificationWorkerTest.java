package io.github.anjali.notifyflow.management.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.anjali.notifyflow.management.entity.Notification;
import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.NotificationDeliveryStatus;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.repository.NotificationProviderRepository;
import io.github.anjali.notifyflow.management.repository.NotificationRepository;
import io.github.anjali.notifyflow.management.service.NotificationTrackingService;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcher;
import io.github.anjali.notifyflow.management.service.dispatch.NotificationDispatcherFactory;

@ExtendWith(MockitoExtension.class)
class NotificationWorkerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProviderRepository providerRepository;

    @Mock
    private NotificationTrackingService trackingService;

    @Mock
    private NotificationDispatcherFactory dispatcherFactory;

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private NotificationWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "processingTimeoutMinutes", 10L);
    }

    @Test
    void claimsQueuedNotificationAndMarksItSentAfterSuccessfulDispatch() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = queuedNotification(notificationId);
        NotificationProvider provider = enabledProvider(notification.getProviderId());

        when(notificationRepository.findIdsByStatus(eq(NotificationDeliveryStatus.QUEUED), any(Pageable.class)))
                .thenReturn(List.of(notificationId));
        when(trackingService.claimQueuedNotification(notificationId)).thenReturn(true);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(providerRepository.findById(notification.getProviderId())).thenReturn(Optional.of(provider));
        when(dispatcherFactory.resolveDispatcher(provider)).thenReturn(dispatcher);

        worker.processQueuedNotifications();

        InOrder order = inOrder(trackingService, dispatcher);
        order.verify(trackingService).claimQueuedNotification(notificationId);
        order.verify(dispatcher).dispatch(provider, notification.getRecipient(), notification.getSubject(), notification.getBody());
        order.verify(trackingService).updateNotificationStatus(notificationId, NotificationDeliveryStatus.SENT);
        order.verify(trackingService).setSentAt(notificationId);
        verify(trackingService, never()).updateNotificationStatusWithFailure(any(), any(), any());
    }

    @Test
    void marksNotificationFailedWhenDispatchThrows() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = queuedNotification(notificationId);
        NotificationProvider provider = enabledProvider(notification.getProviderId());

        when(notificationRepository.findIdsByStatus(eq(NotificationDeliveryStatus.QUEUED), any(Pageable.class)))
                .thenReturn(List.of(notificationId));
        when(trackingService.claimQueuedNotification(notificationId)).thenReturn(true);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(providerRepository.findById(notification.getProviderId())).thenReturn(Optional.of(provider));
        when(dispatcherFactory.resolveDispatcher(provider)).thenReturn(dispatcher);
        org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable"))
                .when(dispatcher).dispatch(any(), any(), any(), any());

        worker.processQueuedNotifications();

        verify(trackingService).updateNotificationStatusWithFailure(
                eq(notificationId), eq(NotificationDeliveryStatus.FAILED),
                org.mockito.ArgumentMatchers.contains("provider unavailable"));
        verify(trackingService, never()).setSentAt(notificationId);
    }

    @Test
    void doesNotProcessFailedWorkAgainWhenItIsNoLongerQueued() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findIdsByStatus(eq(NotificationDeliveryStatus.QUEUED), any(Pageable.class)))
                .thenReturn(List.of(notificationId), List.of());
        when(trackingService.claimQueuedNotification(notificationId)).thenReturn(true);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        worker.processQueuedNotifications();
        worker.processQueuedNotifications();

        verify(trackingService).claimQueuedNotification(notificationId);
        verify(notificationRepository).findById(notificationId);
    }

    @Test
    void onlyOneOfMultipleWorkersProcessesTheSameClaimedNotification() {
        UUID notificationId = UUID.randomUUID();
        Notification notification = queuedNotification(notificationId);
        NotificationProvider provider = enabledProvider(notification.getProviderId());
        NotificationWorker secondWorker = new NotificationWorker(
                notificationRepository, providerRepository, trackingService, dispatcherFactory);
        ReflectionTestUtils.setField(secondWorker, "batchSize", 20);
        ReflectionTestUtils.setField(secondWorker, "processingTimeoutMinutes", 10L);

        when(notificationRepository.findIdsByStatus(eq(NotificationDeliveryStatus.QUEUED), any(Pageable.class)))
                .thenReturn(List.of(notificationId));
        when(trackingService.claimQueuedNotification(notificationId)).thenReturn(true, false);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(providerRepository.findById(notification.getProviderId())).thenReturn(Optional.of(provider));
        when(dispatcherFactory.resolveDispatcher(provider)).thenReturn(dispatcher);

        worker.processQueuedNotifications();
        secondWorker.processQueuedNotifications();

        verify(trackingService, times(2)).claimQueuedNotification(notificationId);
        verify(dispatcher, times(1)).dispatch(any(), any(), any(), any());
    }

    @Test
    void recoversStaleProcessingNotificationsBeforeLookingForQueuedWork() {
        when(notificationRepository.findIdsByStatus(eq(NotificationDeliveryStatus.QUEUED), any(Pageable.class)))
                .thenReturn(List.of());

        worker.processQueuedNotifications();

        InOrder order = inOrder(trackingService, notificationRepository);
        order.verify(trackingService).recoverStuckNotifications(any(LocalDateTime.class));
        order.verify(notificationRepository).findIdsByStatus(eq(NotificationDeliveryStatus.QUEUED), any(Pageable.class));
    }

    private Notification queuedNotification(UUID notificationId) {
        UUID providerId = UUID.randomUUID();
        return Notification.builder()
                .id(notificationId)
                .recipient("user@example.com")
                .channel(NotificationChannel.EMAIL)
                .providerId(providerId)
                .subject("Welcome")
                .body("Hello")
                .status(NotificationDeliveryStatus.QUEUED)
                .retryCount(0)
                .build();
    }

    private NotificationProvider enabledProvider(UUID providerId) {
        return NotificationProvider.builder()
                .id(providerId)
                .name("SendGrid")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SENDGRID)
                .enabled(true)
                .build();
    }
}
