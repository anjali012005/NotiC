package io.github.anjali.notifyflow.management.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void usesConfiguredDelaySequence() {
        RetryPolicyProperties properties = new RetryPolicyProperties();
        properties.setMaxAttempts(3);
        properties.setDelays(List.of("5s", "30s", "2m"));

        RetryPolicy policy = new RetryPolicy(properties);

        assertThat(policy.getDelaySecondsForAttempt(1)).isEqualTo(5);
        assertThat(policy.getDelaySecondsForAttempt(2)).isEqualTo(30);
        assertThat(policy.getDelaySecondsForAttempt(3)).isEqualTo(120);
        assertThat(policy.shouldRetry(2, 3)).isTrue();
        assertThat(policy.shouldRetry(3, 3)).isFalse();
    }
}
