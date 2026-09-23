package io.github.anjali.notifyflow.management.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.anjali.notifyflow.management.entity.OutboxEvent;
import io.github.anjali.notifyflow.management.enums.OutboxEventStatus;
import io.github.anjali.notifyflow.management.messaging.NotificationEventPublisher;
import io.github.anjali.notifyflow.management.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private NotificationEventPublisher eventPublisher;

    @InjectMocks
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "batchSize", 20);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 10);
    }

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findPendingForUpdate(any(), any(Integer.class), any(Pageable.class)))
            .thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(eventPublisher).publishNotificationCreated(event.getAggregateId());
        verify(outboxEventRepository).save(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void keepsEventPendingAndRecordsFailureWhenRabbitMqIsUnavailable() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findPendingForUpdate(any(), any(Integer.class), any(Pageable.class)))
            .thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(eventPublisher).publishNotificationCreated(event.getAggregateId());

        publisher.publishPendingEvents();

        verify(outboxEventRepository).save(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker unavailable");
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("NOTIFICATION_CREATED")
                .aggregateType("NOTIFICATION")
                .aggregateId(UUID.randomUUID())
                .payload("{\"notificationId\":\"test\"}")
                .status(OutboxEventStatus.PENDING)
                .attemptCount(0)
                .build();
    }
}