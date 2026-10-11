package io.github.anjali.notifyflow.management.provider;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.anjali.notifyflow.management.entity.NotificationProvider;
import io.github.anjali.notifyflow.management.exception.ProviderDispatchException;

@Component
public class ProviderProtectionService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ProviderProtectionConfig> configuredProviders = new ConcurrentHashMap<>();
    private final Map<String, ProviderProtectionState> states = new ConcurrentHashMap<>();

    @Value("${notification.providers.default.rate-limit.requests-per-second:20}")
    private int defaultRequestsPerSecond = 20;

    @Value("${notification.providers.default.rate-limit.burst:10}")
    private int defaultBurst = 10;

    @Value("${notification.providers.default.circuit-breaker.failure-threshold:5}")
    private int defaultFailureThreshold = 5;

    @Value("${notification.providers.default.circuit-breaker.open-duration-ms:30000}")
    private long defaultOpenDurationMs = 30_000L;

    @Value("${notification.providers.default.circuit-breaker.half-open-max-calls:2}")
    private int defaultHalfOpenMaxCalls = 2;

    public ProviderProtectionService() {
        // Default configuration is populated after Spring injects the @Value overrides.
    }

    @jakarta.annotation.PostConstruct
    public void initializeDefaults() {
        configuredProviders.put("__default__", buildDefaultConfig());
    }

    public void configure(NotificationProvider provider, ProviderProtectionConfig config) {
        if (provider == null) {
            return;
        }
        config.validate();
        configuredProviders.put(keyFor(provider), config);
        states.computeIfAbsent(keyFor(provider), ignored -> new ProviderProtectionState(config));
    }

    public void beforeCall(NotificationProvider provider) {
        ProviderProtectionConfig config = resolveConfig(provider);
        ProviderProtectionState state = states.computeIfAbsent(keyFor(provider), ignored -> new ProviderProtectionState(config));

        synchronized (state) {
            if (state.circuitState == CircuitState.OPEN) {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - state.openedAtNanos).toMillis();
                if (elapsedMs < config.getOpenDurationMs()) {
                    throw new ProviderDispatchException("Circuit is open for provider " + describe(provider) + ". Retry later.");
                }
                state.circuitState = CircuitState.HALF_OPEN;
                state.halfOpenCalls = 0;
            }

            if (state.circuitState == CircuitState.HALF_OPEN) {
                if (state.halfOpenCalls >= config.getHalfOpenMaxCalls()) {
                    throw new ProviderDispatchException("Circuit is half-open and call budget exhausted for provider " + describe(provider));
                }
            }

            throttle(state.bucket, config);
            if (state.circuitState == CircuitState.HALF_OPEN) {
                state.halfOpenCalls++;
            }
        }
    }

    public void recordSuccess(NotificationProvider provider) {
        ProviderProtectionState state = states.get(keyFor(provider));
        if (state == null) {
            return;
        }

        synchronized (state) {
            state.consecutiveFailures = 0;
            state.circuitState = CircuitState.CLOSED;
            state.halfOpenCalls = 0;
            state.openedAtNanos = 0L;
        }
    }

    public void recordFailure(NotificationProvider provider) {
        ProviderProtectionState state = states.get(keyFor(provider));
        ProviderProtectionConfig config = resolveConfig(provider);
        if (state == null) {
            state = new ProviderProtectionState(config);
            states.put(keyFor(provider), state);
        }

        synchronized (state) {
            state.consecutiveFailures++;

            if (state.circuitState == CircuitState.HALF_OPEN) {
                state.circuitState = CircuitState.OPEN;
                state.openedAtNanos = System.nanoTime();
                state.halfOpenCalls = 0;
                return;
            }

            if (state.consecutiveFailures >= config.getFailureThreshold()) {
                state.circuitState = CircuitState.OPEN;
                state.openedAtNanos = System.nanoTime();
                state.halfOpenCalls = 0;
            }
        }
    }

    private ProviderProtectionConfig buildDefaultConfig() {
        return ProviderProtectionConfig.builder()
                .requestsPerSecond(Math.max(1, defaultRequestsPerSecond))
                .burst(Math.max(1, defaultBurst))
                .failureThreshold(Math.max(1, defaultFailureThreshold))
                .openDurationMs(Math.max(0, defaultOpenDurationMs))
                .halfOpenMaxCalls(Math.max(1, defaultHalfOpenMaxCalls))
                .build();
    }

    private ProviderProtectionConfig resolveConfig(NotificationProvider provider) {
        String key = keyFor(provider);
        ProviderProtectionConfig config = configuredProviders.get(key);
        if (config != null) {
            return config;
        }

        ProviderProtectionConfig defaults = configuredProviders.getOrDefault("__default__", buildDefaultConfig());
        ProviderProtectionConfig merged = mergeWithProviderDefaults(defaults, provider);
        configure(provider, merged);
        return merged;
    }

    private ProviderProtectionConfig mergeWithProviderDefaults(ProviderProtectionConfig base, NotificationProvider provider) {
        var builder = ProviderProtectionConfig.builder()
                .requestsPerSecond(base.getRequestsPerSecond())
                .burst(base.getBurst())
                .failureThreshold(base.getFailureThreshold())
                .openDurationMs(base.getOpenDurationMs())
                .halfOpenMaxCalls(base.getHalfOpenMaxCalls());

        if (provider == null || provider.getConfig() == null || provider.getConfig().isBlank()) {
            return builder.build();
        }

        try {
            Object raw = objectMapper.readValue(provider.getConfig(), Object.class);
            if (!(raw instanceof Map<?, ?> root)) {
                return builder.build();
            }

            Map<?, ?> rateLimit = firstPresentMap(root, "rateLimit", "rate-limit");
            Map<?, ?> circuitBreaker = firstPresentMap(root, "circuitBreaker", "circuit-breaker");

            if (rateLimit != null) {
                Object requestsPerSecond = rateLimit.get("requestsPerSecond");
                if (requestsPerSecond == null) {
                    requestsPerSecond = rateLimit.get("requests-per-second");
                }
                Object burst = rateLimit.get("burst");
                if (requestsPerSecond != null) {
                    builder.requestsPerSecond(asInt(requestsPerSecond, base.getRequestsPerSecond()));
                }
                if (burst != null) {
                    builder.burst(asInt(burst, base.getBurst()));
                }
            }

            if (circuitBreaker != null) {
                Object failureThreshold = circuitBreaker.get("failureThreshold");
                if (failureThreshold == null) {
                    failureThreshold = circuitBreaker.get("failure-threshold");
                }
                Object openDurationMs = circuitBreaker.get("openDurationMs");
                if (openDurationMs == null) {
                    openDurationMs = circuitBreaker.get("open-duration-ms");
                }
                Object halfOpenMaxCalls = circuitBreaker.get("halfOpenMaxCalls");
                if (halfOpenMaxCalls == null) {
                    halfOpenMaxCalls = circuitBreaker.get("half-open-max-calls");
                }
                if (failureThreshold != null) {
                    builder.failureThreshold(asInt(failureThreshold, base.getFailureThreshold()));
                }
                if (openDurationMs != null) {
                    builder.openDurationMs(asLong(openDurationMs, base.getOpenDurationMs()));
                }
                if (halfOpenMaxCalls != null) {
                    builder.halfOpenMaxCalls(asInt(halfOpenMaxCalls, base.getHalfOpenMaxCalls()));
                }
            }

            return builder.build();
        } catch (Exception e) {
            return builder.build();
        }
    }

    private static int asInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long asLong(Object value, long fallback) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Map<?, ?> firstPresentMap(Map<?, ?> root, String camelCaseKey, String kebabCaseKey) {
        Object value = root.get(camelCaseKey);
        if (value == null) {
            value = root.get(kebabCaseKey);
        }
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static String keyFor(NotificationProvider provider) {
        if (provider == null) {
            return "__default__";
        }
        if (provider.getId() != null) {
            return provider.getId().toString();
        }
        return (provider.getProviderType() == null ? "unknown" : provider.getProviderType().name()) + ":" +
                (provider.getName() == null ? "unnamed" : provider.getName());
    }

    private static String describe(NotificationProvider provider) {
        if (provider == null) {
            return "unknown provider";
        }
        return provider.getName() == null ? (provider.getProviderType() == null ? "unknown" : provider.getProviderType().name())
                : provider.getName();
    }

    private void throttle(TokenBucket bucket, ProviderProtectionConfig config) {
        bucket.refill(config.getRequestsPerSecond());
        while (bucket.tokens < 1.0) {
            long waitNanos = Math.max(1_000_000L,
                    (long) ((1.0 - bucket.tokens) * 1_000_000_000.0 / Math.max(1, config.getRequestsPerSecond())));
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderDispatchException("Interrupted while waiting for provider rate limit", e);
            }
            bucket.refill(config.getRequestsPerSecond());
        }
        bucket.tokens = Math.max(0.0, bucket.tokens - 1.0);
    }

    private static final class ProviderProtectionState {
        private final TokenBucket bucket;
        private CircuitState circuitState = CircuitState.CLOSED;
        private int consecutiveFailures = 0;
        private long openedAtNanos = 0L;
        private int halfOpenCalls = 0;

        private ProviderProtectionState(ProviderProtectionConfig config) {
            this.bucket = new TokenBucket(config.getBurst(), config.getRequestsPerSecond());
        }
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int burst, int requestsPerSecond) {
            this.capacity = Math.max(1.0, burst);
            this.refillRatePerSecond = Math.max(1.0, requestsPerSecond);
            this.tokens = this.capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        private void refill(double requestsPerSecond) {
            long now = System.nanoTime();
            long elapsedNanos = now - this.lastRefillNanos;
            this.lastRefillNanos = now;
            if (elapsedNanos <= 0) {
                return;
            }
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double tokensToAdd = elapsedSeconds * Math.max(1.0, requestsPerSecond);
            this.tokens = Math.min(this.capacity, this.tokens + tokensToAdd);
        }
    }

    private enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
