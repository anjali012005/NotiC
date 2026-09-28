package io.github.anjali.notifyflow.management.retry;

public class RetryPolicy {

    private final RetryPolicyProperties properties;

    public RetryPolicy(RetryPolicyProperties properties) {
        this.properties = properties;
    }

    public int getDelaySecondsForAttempt(int attemptNumber) {
        return properties.getDelaySecondsForAttempt(attemptNumber);
    }

    public boolean shouldRetry(int currentAttempt, int maxAttempts) {
        return currentAttempt < maxAttempts;
    }
}
