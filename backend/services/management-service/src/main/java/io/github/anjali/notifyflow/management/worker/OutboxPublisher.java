package io.github.anjali.notifyflow.management.worker;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.anjali.notifyflow.management.entity.OutboxEvent;
import io.github.anjali.notifyflow.management.enums.OutboxEventStatus;
import io.github.anjali.notifyflow.management.messaging.NotificationEventPublisher;
import io.github.anjali.notifyflow.management.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

/** Moves persisted notification events to RabbitMQ; it never dispatches notifications itself. */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationEventPublisher eventPublisher;

    @Value("${notification.outbox.batch-size:20}")
    private int batchSize;

    @Value("${notification.outbox.max-attempts:10}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${notification.outbox.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findPendingForUpdate(
            OutboxEventStatus.PENDING, maxAttempts, PageRequest.of(0, batchSize));

        for (OutboxEvent event : events) {
            try {
                event.setAttemptCount(event.getAttemptCount() + 1);
                eventPublisher.publishNotificationCreated(event.getAggregateId());
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (RuntimeException exception) {
                event.setLastError(exception.getMessage());
            }
            outboxEventRepository.save(event);
        }
    }
}