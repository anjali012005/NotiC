package io.github.anjali.notifyflow.management.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.enums.NotificationChannel;
import io.github.anjali.notifyflow.management.enums.ProviderType;
import io.github.anjali.notifyflow.management.exception.ProviderDispatchException;

class ProviderProtectionServiceTest {

    @Test
    void opensCircuitAfterConfiguredFailureThreshold() {
        ProviderProtectionService service = new ProviderProtectionService();
        NotificationProvider provider = provider();
        service.configure(provider, ProviderProtectionConfig.builder()
                .requestsPerSecond(100)
                .burst(10)
                .failureThreshold(2)
                .openDurationMs(30000)
                .halfOpenMaxCalls(1)
                .build());

        service.recordFailure(provider);
        service.recordFailure(provider);

        ProviderDispatchException exception = assertThrows(ProviderDispatchException.class,
                () -> service.beforeCall(provider));
        assertTrue(exception.getMessage().toLowerCase().contains("circuit"));
    }

    @Test
    void throttlesCallsAboveConfiguredRate() throws Exception {
        ProviderProtectionService service = new ProviderProtectionService();
        NotificationProvider provider = provider();
        service.configure(provider, ProviderProtectionConfig.builder()
                .requestsPerSecond(10)
                .burst(1)
                .failureThreshold(5)
                .openDurationMs(30000)
                .halfOpenMaxCalls(1)
                .build());

        long start = System.nanoTime();
        service.beforeCall(provider);
        service.beforeCall(provider);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(elapsedMs >= 80, "Expected throttling delay for rate-limited provider calls; elapsed was " + elapsedMs + "ms");
    }

    private NotificationProvider provider() {
        return NotificationProvider.builder()
                .id(UUID.randomUUID())
                .name("SendGrid")
                .channel(NotificationChannel.EMAIL)
                .providerType(ProviderType.SENDGRID)
                .config("{\"apiKey\":\"test\",\"fromEmail\":\"from@example.com\"}")
                .enabled(true)
                .build();
    }
}
