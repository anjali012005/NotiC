package io.github.anjali.notifyflow.management.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "notification.retry")
@Getter
@Setter
public class RetryPolicyProperties {

    private int maxAttempts = 3;
    private List<String> delays = new ArrayList<>(List.of("5s", "30s", "2m"));

    public Duration getDelayForAttempt(int attemptNumber) {
        List<String> normalizedDelays = delays == null || delays.isEmpty() ? List.of("5s", "30s", "2m") : delays;
        int index = Math.max(0, Math.min(attemptNumber - 1, normalizedDelays.size() - 1));
        return parseDelay(normalizedDelays.get(index));
    }

    public int getDelaySecondsForAttempt(int attemptNumber) {
        return (int) getDelayForAttempt(attemptNumber).getSeconds();
    }

    private Duration parseDelay(String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.ofSeconds(Long.parseLong(normalized));
    }
}
