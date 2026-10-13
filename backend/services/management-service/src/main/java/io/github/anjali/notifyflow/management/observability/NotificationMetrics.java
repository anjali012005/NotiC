package io.github.anjali.notifyflow.management.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordNotificationAccepted(String channel) {
        counter("notifyflow.notifications.accepted", "channel", safe(channel)).increment();
    }

    public void recordNotificationDelivered(String channel) {
        counter("notifyflow.notifications.delivered", "channel", safe(channel)).increment();
    }

    public void recordNotificationFailed(String channel, String failureCategory) {
        counter("notifyflow.notifications.failed", "channel", safe(channel), "failureCategory", safe(failureCategory)).increment();
    }

    public void recordRetryAttempt(String channel) {
        counter("notifyflow.notifications.retry.attempts", "channel", safe(channel)).increment();
    }

    public void recordProcessingError(String channel, String failureCategory) {
        counter("notifyflow.notifications.errors", "channel", safe(channel), "errorCategory", safe(failureCategory)).increment();
    }

    public void recordProviderDispatch(String providerType, String channel, String outcome, String statusCode) {
        counter("notifyflow.providers.dispatch",
                "providerType", safe(providerType),
                "channel", safe(channel),
                "outcome", safe(outcome),
                "statusCode", safe(statusCode))
                .increment();
    }

    public void recordProviderFailure(String providerType, String channel, String failureCategory) {
        counter("notifyflow.providers.failure",
                "providerType", safe(providerType),
                "channel", safe(channel),
                "failureCategory", safe(failureCategory))
                .increment();
    }

    public void recordProviderDispatchLatency(String providerType, String channel, Duration duration) {
        Timer.builder("notifyflow.providers.dispatch.latency")
                .tag("providerType", safe(providerType))
                .tag("channel", safe(channel))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordNotificationProcessingLatency(String channel, Duration duration) {
        Timer.builder("notifyflow.notifications.processing.latency")
                .tag("channel", safe(channel))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordOutboxPublicationSuccess() {
        counter("notifyflow.outbox.published").increment();
    }

    public void recordOutboxPublicationFailure() {
        counter("notifyflow.outbox.failed").increment();
    }

    protected Counter counter(String name, String... tags) {
        return Counter.builder(name)
                .tags(tags)
                .register(meterRegistry);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.trim();
        return sanitized.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
