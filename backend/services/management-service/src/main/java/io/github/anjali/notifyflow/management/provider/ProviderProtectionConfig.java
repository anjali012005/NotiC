package io.github.anjali.notifyflow.management.provider;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProviderProtectionConfig {
    @Builder.Default
    private int requestsPerSecond = 20;

    @Builder.Default
    private int burst = 10;

    @Builder.Default
    private int failureThreshold = 5;

    @Builder.Default
    private long openDurationMs = 30_000L;

    @Builder.Default
    private int halfOpenMaxCalls = 2;

    public void validate() {
        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("requestsPerSecond must be greater than zero");
        }
        if (burst <= 0) {
            throw new IllegalArgumentException("burst must be greater than zero");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be greater than zero");
        }
        if (openDurationMs < 0) {
            throw new IllegalArgumentException("openDurationMs must be zero or greater");
        }
        if (halfOpenMaxCalls <= 0) {
            throw new IllegalArgumentException("halfOpenMaxCalls must be greater than zero");
        }
    }
}
