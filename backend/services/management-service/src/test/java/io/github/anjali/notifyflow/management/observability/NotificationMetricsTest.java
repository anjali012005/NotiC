package io.github.anjali.notifyflow.management.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class NotificationMetricsTest {

    @Test
    void recordsBoundedNotificationAndProviderMetrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new NotificationMetrics(registry);

        metrics.recordNotificationAccepted("EMAIL");
        metrics.recordProviderDispatch("SENDGRID", "EMAIL", "SUCCESS", "OK");
        metrics.recordProviderFailure("SENDGRID", "EMAIL", "TIMEOUT");

        assertThat(registry.find("notifyflow.notifications.accepted").counter()).isNotNull();
        assertThat(registry.find("notifyflow.providers.dispatch").tag("providerType", "SENDGRID")
                .tag("outcome", "SUCCESS").counter()).isNotNull();
        assertThat(registry.find("notifyflow.providers.failure").tag("providerType", "SENDGRID")
                .tag("failureCategory", "TIMEOUT").counter()).isNotNull();
    }
}
