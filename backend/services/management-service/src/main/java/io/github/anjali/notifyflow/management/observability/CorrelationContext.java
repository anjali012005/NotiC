package io.github.anjali.notifyflow.management.observability;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

public final class CorrelationContext {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private CorrelationContext() {
    }

    public static String currentOrCreate() {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        String generated = generateId();
        setCurrent(generated);
        return generated;
    }

    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.trim();
        if (sanitized.isEmpty()) {
            return null;
        }
        if (!VALID_PATTERN.matcher(sanitized).matches()) {
            return null;
        }
        return sanitized;
    }

    public static void setCurrent(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            normalized = generateId();
        }
        MDC.put(MDC_KEY, normalized);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
