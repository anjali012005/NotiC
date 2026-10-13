package io.github.anjali.notifyflow.management.observability;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.anjali.notifyflow.management.enums.OutboxEventStatus;
import io.github.anjali.notifyflow.management.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        Gauge.builder("notifyflow.outbox.pending", outboxEventRepository,
                repository -> repository.countByStatus(OutboxEventStatus.PENDING))
                .register(meterRegistry);

        Gauge.builder("notifyflow.outbox.oldest.pending.seconds", outboxEventRepository, this::pendingAgeSeconds)
                .register(meterRegistry);
    }

    private double pendingAgeSeconds(OutboxEventRepository repository) {
        Optional<LocalDateTime> oldest = repository.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING);
        if (oldest.isEmpty()) {
            return 0.0;
        }
        return Math.max(0.0, Duration.between(oldest.get(), LocalDateTime.now()).toMillis() / 1000.0);
    }
}
